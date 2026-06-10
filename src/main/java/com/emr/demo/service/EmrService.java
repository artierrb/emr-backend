package com.emr.demo.service;

import com.emr.demo.model.*;
import com.emr.demo.repository.EmrRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class EmrService {

    private static final Logger log = LoggerFactory.getLogger(EmrService.class);
    private final EmrRepository repo;
    private final FileService fileService;
    private final com.emr.demo.util.JwtUtil jwtUtil;

    public EmrService(EmrRepository repo, FileService fileService, com.emr.demo.util.JwtUtil jwtUtil) {
        this.repo = repo;
        this.fileService = fileService;
        this.jwtUtil = jwtUtil;
    }

    // ─── Viewer ───────────────────────────────────────────────

    public List<Treatment> getTreatments(String hn) {
        return repo.getTreatmentsByPatId(hn.trim().toUpperCase());
    }

    public List<Map<String, Object>> getTreatmentsFull(String hn) {
        return repo.getTreatmentsFull(hn.trim().toUpperCase());
    }

    public List<ChartPage> getChartPages(long treatNo) {
        List<ChartPage> pages = repo.getChartPagesByTreatNo(treatNo);
        for (ChartPage cp : pages) {
            cp.setFilePath("/api/image/" + cp.getPageNo() +
                    "?ext=" + (cp.getExtension() != null ? cp.getExtension() : "jpg"));
        }
        return pages;
    }

    public List<Map<String, Object>> getFormCountByTreatNo(long treatNo) {
        return repo.getFormCountByTreatNo(treatNo);
    }

    public List<Form> getForms() { return repo.getActiveForms(); }

    // ─── Treat CRUD ───────────────────────────────────────────

    public void updateTreatCheck(long treatNo, int checkNo, String value, String userId) {
        repo.updateTreatCheck(treatNo, checkNo, value, userId);
    }

    public long insertTreatmentFull(String patId, String inDate, String clinCode,
                                    String docCode, String classType,
                                    String vstNum, String admNum, String userId) {
        return repo.insertTreatmentFull(patId, inDate, clinCode, docCode, classType, vstNum, admNum, userId);
    }

    public void moveChartPages(java.util.List<Long> pageNos, String newFormCode) {
        repo.moveChartPages(pageNos, newFormCode);
    }

    public void deleteTreatment(long treatNo) {
        repo.deleteTreatment(treatNo);
    }

    // ─── CLINICT / USERT search ───────────────────────────────

    public List<Map<String, Object>> searchClinic(String keyword) {
        return repo.searchClinic(keyword == null ? "" : keyword);
    }

    public List<Map<String, Object>> searchUser(String keyword) {
        return repo.searchUser(keyword == null ? "" : keyword);
    }

    // ─── OCR Print ────────────────────────────────────────────────

    public List<Map<String, Object>> getOcrPrintSetup(String inputGubun, String loginUserId, String loginClinic) {
        return repo.getOcrPrintSetup(inputGubun, loginUserId, loginClinic);
    }

    public boolean checkReprint(String ocmNum, String formCode) {
        return repo.checkReprint(ocmNum, formCode);
    }

    public List<Map<String, Object>> getReprintReasons() {
        return repo.getReprintReasons();
    }

    public void printOcr(String ocmNum, String outDate, String formCode,
                         String userId, String reason, String patType,
                         String patId, String inDate, String clinCode, String docCode,
                         Long treatNo) {
        // get next OCRPK (locked, sequential 13-digit)
        String ocrPk = repo.getNextOcrPk();

        // get HOST_ID() for PrmRptNam
        String hostId = repo.getHostId();

        // get OCRYN from FORMT
        String ocrYn = repo.getFormOcrYn(formCode);

        // insert PrmTmp
        repo.insertPrmTmp(hostId, formCode, ocmNum, outDate, ocrYn, ocrPk, userId);

        // insert OCRPRINT log
        repo.insertOcrPrint(ocrPk, patId, inDate, clinCode, docCode, formCode, userId, ocmNum, patType);

        // shell EMRPrint.exe — pass hostId so it can exact-match PrmRptNam
        shellEmrPrint(formCode, ocrPk, hostId);

        // insert PAGET + CHARTPAGET after OCRPK updated
        insertOcrPages(formCode, ocrPk, userId, treatNo);
    }

    private void insertOcrPages(String formCode, String ocrPk, String userId, Long treatNo) {
        if (treatNo == null || treatNo <= 0) return;
        try {
            String today    = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
            String grpMid   = repo.getFormGrpMid(formCode);
            PathConfig path = repo.getActivePath();

            // insert PAGET (no file yet — fileSize=0, ext='')
            long pageNo;
            int attempt = 0;
            while (true) {
                attempt++;
                if (attempt > 100) throw new RuntimeException("Cannot find available PAGENO after " + attempt + " attempts");
                long candidate = repo.peekNextPageNo();
                pageNo = repo.insertPage(path.getPathId(), today, userId, 0L, "", ocrPk);
                log.info("OCR PageNo={} inserted for OCRPK={}", pageNo, ocrPk);
                break;
            }

            // insert CHARTPAGET
            repo.insertChartPage(pageNo, treatNo, formCode, today, userId, grpMid);
        } catch (Exception e) {
            log.error("insertOcrPages failed: {}", e.getMessage(), e);
        }
    }

    private void shellEmrPrint(String formCode, String ocrPk, String hostId) {
        String emrPrintPath = repo.getConfigValue("EMRPRTPATH");
        if (emrPrintPath == null || emrPrintPath.isBlank()) {
            emrPrintPath = "EMRPrint.exe";
        } else {
            emrPrintPath = emrPrintPath.trim();
            if (!emrPrintPath.endsWith("\\") && !emrPrintPath.endsWith("/")) emrPrintPath += "\\";
            emrPrintPath += "EMRPrint.exe";
        }
        try {
            String[] cmd = { emrPrintPath, formCode, ocrPk, hostId };
            Process process = Runtime.getRuntime().exec(cmd);
            boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("EMRPrint.exe timeout after 30s");
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String errMsg = new String(process.getErrorStream().readAllBytes());
                throw new RuntimeException("EMRPrint.exe exit " + exitCode + ": " + errMsg);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to run EMRPrint.exe: " + e.getMessage(), e);
        }
    }

    public byte[] getPdfBytes(String formCode) throws java.io.IOException {
        String prepth = repo.getPrepth().trim();
        if (prepth.isEmpty()) throw new java.io.IOException("PREPTH config not found");
        if (!prepth.endsWith("\\") && !prepth.endsWith("/")) prepth += "\\";
        String filePath = prepth + formCode + ".pdf";
        java.io.File file = new java.io.File(filePath);
        if (!file.exists()) throw new java.io.IOException("PDF not found: " + filePath);
        return java.nio.file.Files.readAllBytes(file.toPath());
    }

    // ─── Viewer ───────────────────────────────────────────────────

    public List<Map<String, Object>> getViewerTreatments(String hn) {
        return repo.getViewerTreatments(hn.trim().toUpperCase());
    }

    public List<Map<String, Object>> getViewerPages(String hn, String formCode, String classFilter) {
        return repo.getViewerPages(hn.trim().toUpperCase(), formCode, classFilter);
    }

    public List<Map<String, Object>> getViewerPagesByTreat(long treatNo, String formCode) {
        return repo.getViewerPagesByTreat(treatNo, formCode);
    }

    // ─── Scan ─────────────────────────────────────────────────

    @Transactional
    public long saveScan(String hn, String formCode, String grpMid,
                         Long treatNoParam, MultipartFile file, String userId) throws IOException {

        PathConfig pathConfig = repo.getActivePath();
        String today = fileService.getTodayString();
        String ext = fileService.getExtension(file.getOriginalFilename());

        if (treatNoParam == null || treatNoParam <= 0) {
            throw new IllegalArgumentException("กรุณาเลือกแฟ้มผู้ป่วยก่อน scan");
        }

        long treatNo = treatNoParam;
        log.info("Using TREATNO={} for HN={}", treatNo, hn);

        long pageNo;
        int attempt = 0;
        while (true) {
            attempt++;
            if (attempt > 100) throw new IOException("Cannot find available PAGENO after " + attempt + " attempts");
            long candidate = repo.peekNextPageNo();
            if (!fileService.fileExists(candidate, ext)) {
                pageNo = repo.insertPage(pathConfig.getPathId(), today, userId, 0L, ext, null);
                log.info("PageNo={} saved (attempt {})", pageNo, attempt);
                break;
            }
            log.warn("PageNo={} file already exists, reseeding...", candidate);
            repo.reseedPageNo(candidate);
        }

        fileService.saveFile(file, pageNo, ext);
        repo.insertChartPage(pageNo, treatNo, formCode, today, userId, grpMid);
        repo.updateScanLastTime(treatNo);
        return pageNo;
    }

    // ─── Image ────────────────────────────────────────────────

    public byte[] getImageBytes(long pageNo, String ext) throws IOException {
        return fileService.readFile(pageNo, ext);
    }

    // ─── Config ───────────────────────────────────────────────

    public List<Map<String, Object>> getConfig(String search) { return repo.getConfig(search); }

    public void saveConfig(List<Map<String, String>> items, String userId) {
        for (Map<String, String> item : items) {
            repo.saveConfigItem(item.get("dtlCod"), item.get("dtlCodVal"), userId);
        }
    }

    public String getConfigValue(String dtlCod) { return repo.getConfigValue(dtlCod); }

    public String getPrgMode() {
        String val = repo.getConfigValue("PRGMODE");
        return (val == null || val.isBlank()) ? "LITE" : val.trim().toUpperCase();
    }

    // ─── Detail Master queries ────────────────────────────────

    public List<Map<String, Object>> getTabTyp()                   { return repo.getTabTyp(); }
    public List<Map<String, Object>> getTabMst(String t)           { return repo.getTabMst(t); }
    public List<Map<String, Object>> getDtlMst(String t)           { return repo.getDtlMst(t); }
    public List<Map<String, Object>> getDtsMst(String t, String c) { return repo.getDtsMst(t, c); }

    // ─── TabMst CRUD ──────────────────────────────────────────

    public void insertTabMst(String a, String b, String c) { repo.insertTabMst(a, b, c); }
    public void updateTabMst(String a, String b, String c) { repo.updateTabMst(a, b, c); }
    public void deleteTabMst(String a)                     { repo.deleteTabMst(a); }

    // ─── DtlMst CRUD ──────────────────────────────────────────

    public int getNextDtlDspSeq(String t)                                                      { return repo.getNextDtlDspSeq(t); }
    public void insertDtlMst(String t, String c, String n, String v, int s, String u)          { repo.insertDtlMst(t, c, n, v, s, u); }
    public void updateDtlMst(String t, String c, String n, String v, String u)                 { repo.updateDtlMst(t, c, n, v, u); }
    public void deleteDtlMst(String t, String c)                                               { repo.deleteDtlMst(t, c); }
    public void reorderDtlMst(String t, List<Map<String, String>> items) {
        for (int i = 0; i < items.size(); i++) repo.updateDtlDspSeq(t, items.get(i).get("dtlCod"), i + 1);
    }

    // ─── DtsMst CRUD ──────────────────────────────────────────

    public int getNextDtsDspSeq(String t, String c)                                                    { return repo.getNextDtsDspSeq(t, c); }
    public void insertDtsMst(String t, String c, String s, String n, String v, int seq, String u)      { repo.insertDtsMst(t, c, s, n, v, seq, u); }
    public void updateDtsMst(String t, String c, String s, String n, String v, String u)               { repo.updateDtsMst(t, c, s, n, v, u); }
    public void deleteDtsMst(String t, String c, String s)                                             { repo.deleteDtsMst(t, c, s); }
    public void reorderDtsMst(String t, String c, List<Map<String, String>> items) {
        for (int i = 0; i < items.size(); i++) repo.updateDtsDspSeq(t, c, items.get(i).get("dtsSubCod"), i + 1);
    }

    // ─── Auth ─────────────────────────────────────────────────────

    public Map<String, Object> login(String userId, String plainPassword) {
        Map<String, Object> user = repo.findUserById(userId);
        if (user == null) return null;

        //String edate = user.getOrDefault("EDATE", "").toString().trim();
        //20260610 Arty
        String edate = user.get("EDATE") != null ? user.get("EDATE").toString().trim() : "";

        if (!edate.isBlank()) {
            try {
                String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
                if (edate.compareTo(today) < 0) return null;
            } catch (Exception ignored) {}
        }

        //String stored = user.getOrDefault("PASSWD", "").toString();
        //Arty 20260610
        String stored = user.get("PASSWD") != null ? user.get("PASSWD").toString() : "";
        String dbUserId = user.getOrDefault("USERID", userId).toString().trim();
        if (!com.emr.demo.util.Cryptograph.verify(dbUserId, plainPassword, stored)) return null;

        return user;
    }

    // ─── User Management ──────────────────────────────────────────

    public List<Map<String, Object>> searchUsers(String field, String keyword) {
        return repo.searchUsers(field, keyword);
    }

    public void insertUser(String userId, String plainPwd, String name,
                           String auth, String clinCode, String edate) {
        String enc = com.emr.demo.util.Cryptograph.encrypt(userId, plainPwd);
        repo.insertUser(userId, enc, name, auth, clinCode, edate);
    }

    public void updateUser(String userId, String name, String auth,
                           String clinCode, String edate) {
        repo.updateUser(userId, name, auth, clinCode, edate);
    }

    public void updateUserPassword(String userId, String plainPwd) {
        String enc = com.emr.demo.util.Cryptograph.encrypt(userId, plainPwd);
        repo.updateUserPassword(userId, enc);
    }

    public void deleteUser(String userId) { repo.deleteUser(userId); }

    public void userInterfaceFromHis() { repo.userInterfaceFromHis(); }

    // ─── PATIENTT ─────────────────────────────────────────────

    public String getHnSepConfig() { return repo.getHnSepConfig(); }

    public List<Map<String, Object>> searchPatient(String field, String keyword) {
        return repo.searchPatient(field, keyword);
    }

    public void insertPatient(String patId, String name, String sex,
                              String jumiNno, String birthDate, String userId) {
        int age = calcAge(birthDate);
        repo.insertPatient(patId, name, sex, jumiNno, birthDate, age, userId);
    }

    public boolean patientExists(String patId) { return repo.patientExists(patId); }

    private int calcAge(String birthDate) {
        if (birthDate == null || birthDate.length() < 4) return 0;
        try {
            return java.time.LocalDate.now().getYear() - Integer.parseInt(birthDate.substring(0, 4));
        } catch (Exception e) { return 0; }
    }

    // ─── EMRScan Integration ──────────────────────────────────────────────────

    // In-memory scan status store (treatNo+formCode → timestamp)
    private final java.util.concurrent.ConcurrentHashMap<String, Long> scanCompleteMap
            = new java.util.concurrent.ConcurrentHashMap<>();

    public String generateScanToken(String userId, String auth, String name) {
        // short-lived JWT — 5 minutes
        return jwtUtil.generateShortLived(userId, auth, name, 5);
    }

    public java.util.Map<String, Object> verifyScanToken(String token) {
        if (!jwtUtil.isValid(token)) return null;
        io.jsonwebtoken.Claims claims = jwtUtil.parse(token);
        return java.util.Map.of(
                "userId", claims.getSubject(),
                "auth",   claims.get("auth", String.class),
                "name",   claims.get("name", String.class)
        );
    }

    public void notifyScanComplete(String treatNo, String formCode, String userId) {
        String key = (treatNo == null ? "" : treatNo) + ":" + (formCode == null ? "" : formCode);
        scanCompleteMap.put(key, System.currentTimeMillis());
        log.info("ScanComplete: treatNo={} formCode={} userId={}", treatNo, formCode, userId);
    }

    public java.util.Map<String, Object> getScanStatus(String treatNo, String formCode) {
        String key = (treatNo == null ? "" : treatNo) + ":" + (formCode == null ? "" : formCode);
        Long ts = scanCompleteMap.get(key);
        if (ts != null) {
            // consume — remove after read so next poll returns false
            scanCompleteMap.remove(key);
            return java.util.Map.of("complete", true, "timestamp", ts);
        }
        return java.util.Map.of("complete", false);
    }

}