package com.emr.backend.service;

import com.emr.backend.model.*;
import com.emr.backend.repository.EmrRepository;
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

    private final com.emr.backend.util.JwtUtil jwtUtil;

    // อ่านค่าจาก application.properties — default = false (ปิด) เพื่อความปลอดภัย
    // ถ้าไม่ตั้งใน properties เลย จะถือว่าปิดไว้
    @org.springframework.beans.factory.annotation.Value("${emr.allow-admin-plaintext:false}")
    private boolean allowAdminPlaintext;

    public EmrService(EmrRepository repo, FileService fileService, com.emr.backend.util.JwtUtil jwtUtil) {
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
        String ocmNum = repo.getNextEOcmNum();
        return repo.insertTreatmentFull(patId, inDate, clinCode, docCode, classType, vstNum, admNum, userId, ocmNum);
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

    // คืน ocrPk ที่เพิ่งสร้าง เพื่อให้ frontend เอาไปเปิด emrprint:// ที่ client
    // หมายเหตุ (ทาง A2): insert PrmTmp ย้ายไปทำที่ client (EMRPrint.exe) ผ่าน ODBC
    //   เพราะ PrmTmp.PrmRptNam ผูกกับ HOST_ID() ซึ่งต้องเป็น host ของเครื่องที่พิมพ์
    //   ส่วน OCRPRINT (IMGEMR, ไม่ผูก HOST_ID) ยัง insert ที่ server ได้ตามเดิม
    public String printOcr(String ocmNum, String outDate, String formCode,
                           String userId, String reason, String patType,
                           String patId, String inDate, String clinCode, String docCode,
                           Long treatNo) {
        // get next OCRPK (locked, sequential 13-digit)
        String ocrPk = repo.getNextOcrPk();

        // CLINCODE/DOCCODE: ดึงจาก TREATT ที่เลือกอยู่ (source of truth) เมื่อมี treatNo
        // ถ้าหาไม่เจอ ค่อย fallback ไปใช้ค่าที่ส่งมาจาก frontend
        String resolvedClinCode = clinCode;
        String resolvedDocCode  = docCode;
        if (treatNo != null && treatNo > 0) {
            Map<String, Object> td = repo.getTreatClinDoc(treatNo);
            if (td != null) {
                Object cc = td.get("CLINCODE");
                Object dc = td.get("DOCCODE");
                if (cc != null && !cc.toString().isBlank()) resolvedClinCode = cc.toString().trim();
                if (dc != null && !dc.toString().isBlank()) resolvedDocCode  = dc.toString().trim();
            }
        }

        // insert OCRPRINT log (with reason → RPTMEMO)
        // เก็บ OCRPK, FORMCODE, OCMNUM, INDATE, PRINTUSERID ครบ — EMRPrint จะ query กลับมาประกอบ PrmTmp
        repo.insertOcrPrint(ocrPk, patId, inDate, resolvedClinCode, resolvedDocCode, formCode, userId, ocmNum, patType, reason);

        // ไม่ insert PrmTmp และไม่ shell EMRPrint.exe ที่ server แล้ว
        // frontend จะเอา ocrPk + token ไปเปิด emrprint:// ที่เครื่อง client
        return ocrPk;
    }

    // ─── Print Token (สำหรับ EMRPrint.exe ที่ client — pattern เดียวกับ scan token) ──

    public String generatePrintToken(String userId, String auth, String name) {
        // short-lived JWT — 5 minutes
        return jwtUtil.generateShortLived(userId, auth, name, 5);
    }

    public java.util.Map<String, Object> verifyPrintToken(String token) {
        if (!jwtUtil.isValid(token)) return null;
        io.jsonwebtoken.Claims claims = jwtUtil.parse(token);
        return java.util.Map.of(
                "userId", claims.getSubject(),
                "auth",   claims.get("auth", String.class),
                "name",   claims.get("name", String.class)
        );
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

    // signature (lastModified+size) สำหรับทำ ETag — ไม่ต้องอ่านทั้งไฟล์
    public String getImageSignature(long pageNo, String ext) throws IOException {
        return fileService.getFileSignature(pageNo, ext);
    }

    // thumbnail (resize ~240px) — ถ้า resize ไม่ได้ (เช่น tiff) controller จะ fallback ภาพเต็ม
    public byte[] getThumbnailBytes(long pageNo, String ext) throws IOException {
        return fileService.readThumbnail(pageNo, ext);
    }

    // ภาพเต็มขนาดแปลงเป็น JPEG — สำหรับ viewer zoom ที่ต้องแสดง TIFF บน Chrome/Edge
    public byte[] getImageAsJpeg(long pageNo, String ext) throws IOException {
        return fileService.readAsJpeg(pageNo, ext);
    }

    // ─── Watermark ────────────────────────────────────────────

    // คืน config watermark สำหรับ frontend: enabled + opacity (0..1)
    public Map<String, Object> getWatermarkConfig() {
        String flag = repo.getWatermarkFlag();
        boolean enabled = "Y".equalsIgnoreCase(flag == null ? "N" : flag.trim());
        // opacity: WTRMRKOVL เก็บเป็น % (เช่น 20) → 0.20 ; ถ้าว่าง/ผิด default 0.20
        double opacity = 0.20;
        try {
            String ovl = repo.getWatermarkOverlay();
            if (ovl != null && !ovl.trim().isEmpty()) {
                double v = Double.parseDouble(ovl.trim());
                if (v > 1.0) v = v / 100.0;          // ถ้าเป็น % (20) → 0.20
                if (v < 0) v = 0; if (v > 1) v = 1;  // clamp 0..1
                opacity = v;
            }
        } catch (Exception ignored) {}
        return Map.of("enabled", enabled, "opacity", opacity);
    }

    // อ่านไฟล์ watermark จาก WTRMRKPTH (server path) — รองรับ jpg/png
    // คืน null ถ้าปิด/ไม่มี path/ไฟล์ไม่เจอ → controller ตอบ 404
    public byte[] getWatermarkBytes() throws IOException {
        String flag = repo.getWatermarkFlag();
        if (!"Y".equalsIgnoreCase(flag == null ? "N" : flag.trim())) return null;
        String path = repo.getWatermarkPath();
        if (path == null || path.trim().isEmpty()) return null;
        java.io.File f = new java.io.File(path.trim());
        if (!f.exists() || !f.isFile()) {
            log.warn("Watermark file not found: {}", path);
            return null;
        }
        return java.nio.file.Files.readAllBytes(f.toPath());
    }

    // เดา content-type จากนามสกุลไฟล์ watermark
    public String getWatermarkContentType() {
        String path = repo.getWatermarkPath();
        if (path == null) return "image/png";
        String p = path.trim().toLowerCase();
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".gif")) return "image/gif";
        return "image/png";   // default png
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

        // 1) verify ปกติ (encrypt แบบ Java Cryptograph)
        boolean ok = com.emr.backend.util.Cryptograph.verify(dbUserId, plainPassword, stored);

        // 2) Fallback admin plain-text (เปิดด้วย flag emr.allow-admin-plaintext เท่านั้น)
        //    เหตุผล: ระบบเก่า VB6 encrypt คนละ algorithm ทุก user ต้องตั้ง pwd ใหม่
        //    admin ต้องเข้าได้ครั้งแรกเพื่อไปจัดการ user — bootstrap only
        //    ตั้ง emr.allow-admin-plaintext=false หลัง setup เสร็จ
        if (!ok && allowAdminPlaintext && "admin".equalsIgnoreCase(dbUserId)) {
            ok = stored.trim().equals(plainPassword);
            if (ok) log.warn("ADMIN logged in via PLAIN-TEXT fallback — " +
                    "ปิด emr.allow-admin-plaintext หลัง setup เสร็จ");
        }

        if (!ok) return null;

        return user;
    }

    // ─── User Management ──────────────────────────────────────────

    public List<Map<String, Object>> searchUsers(String field, String keyword) {
        return repo.searchUsers(field, keyword);
    }

    public void insertUser(String userId, String plainPwd, String name,
                           String auth, String clinCode, String edate) {
        String enc = com.emr.backend.util.Cryptograph.encrypt(userId, plainPwd);
        repo.insertUser(userId, enc, name, auth, clinCode, edate);
    }

    public void updateUser(String userId, String name, String auth,
                           String clinCode, String edate) {
        repo.updateUser(userId, name, auth, clinCode, edate);
    }

    public void updateUserPassword(String userId, String plainPwd) {
        String enc = com.emr.backend.util.Cryptograph.encrypt(userId, plainPwd);
        repo.updateUserPassword(userId, enc);
    }

    public void deleteUser(String userId) { repo.deleteUser(userId); }

    public void userInterfaceFromHis() { repo.userInterfaceFromHis(); }

    // ─── OCS Launch ───────────────────────────────────────────────
    // เปิดจาก OCS (VB6) ผ่าน URL พร้อม param. Backend เป็นคนตัดสินใจทั้งหมด ไม่ trust ค่าดิบจาก URL:
    //   1) เช็ค IP whitelist (ถ้าเปิด config IPWHITELST=Y)
    //   2) sync HIS เฉพาะ user + patient + treat ที่เกี่ยวข้อง
    //   3) verify USERID มีจริง + ไม่หมดอายุ
    //   4) verify PATID+OCMNUM มีคู่กันใน TREATT (กันปลอม) แล้วได้ treat เต็มกลับมา
    //   5) force auth='0' (view-only) เสมอ — แม้ USERT จะเป็น admin
    // คืน Map ที่มี userId/name/auth(=0)/treat(object) ให้ controller เอาไปออก token + ส่งกลับ frontend
    // ถ้า fail จะ throw RuntimeException พร้อมข้อความ — controller แปลงเป็น error response
    public Map<String, Object> ocsLaunch(String patId, String userId, String clinCode,
                                         String inDate, String ocmNum, String clientIp) {
        // 1) IP whitelist
        String wlFlag = repo.getIpWhitelistFlag();
        if ("Y".equalsIgnoreCase(wlFlag == null ? "N" : wlFlag.trim())) {
            String ranges = repo.getWhitelistRanges();
            if (!isIpAllowed(clientIp, ranges)) {
                log.warn("OCS launch BLOCKED — IP [{}] not in whitelist [{}]", clientIp, ranges);
                throw new RuntimeException("ไม่อนุญาตให้เข้าใช้งานจากเครื่องนี้ (IP not allowed)");
            }
        }

        // 2) sync HIS (user + patient + treat) — ห้าม trim patId/ocmNum (padded), userId trim ได้
        String uid = userId == null ? "" : userId.trim();
        repo.interfaceUsertByUserId(uid);
        repo.interfaceByPatId(patId);   // sync TREATT + PATIENTT by PATID (padded)

        // 3) verify user มีจริง + ไม่หมดอายุ
        Map<String, Object> user = repo.findUserById(uid);
        if (user == null) {
            throw new RuntimeException("ไม่พบผู้ใช้ในระบบ (USERID: " + uid + ")");
        }
        String edate = user.get("EDATE") != null ? user.get("EDATE").toString().trim() : "";
        if (!edate.isBlank()) {
            try {
                String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
                if (edate.compareTo(today) < 0) throw new RuntimeException("บัญชีผู้ใช้หมดอายุ");
            } catch (RuntimeException re) { throw re; }
            catch (Exception ignored) {}
        }

        // 4) verify PATID+OCMNUM มีคู่กันใน TREATT
        Map<String, Object> treat = repo.findTreatByPatIdOcmNum(patId, ocmNum);
        if (treat == null) {
            throw new RuntimeException("ไม่พบข้อมูลการรักษาที่ตรงกับ HN และ OCMNUM ที่ส่งมา");
        }

        // 5) force view-only
        String name = user.getOrDefault("NAME", uid).toString().trim();

        // 6) Access control 4 ชั้น — คืน accessStatus: OK / BLOCKSECURE / BLOCKRENT
        String treatInDate = treat.get("INDATE") != null ? treat.get("INDATE").toString().trim() : "";
        String treatClass  = treat.get("CLASS")  != null ? treat.get("CLASS").toString().trim()  : "";
        Map<String, Object> access = checkOcsAccess(uid, patId, treatInDate, treatClass);

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("userId", uid);
        result.put("name",   name);
        result.put("auth",   "0");          // force viewer เสมอ
        result.put("treat",  treat);        // treat object เต็ม (มี TREATNO) ให้ frontend fill viewer
        result.putAll(access);              // accessStatus (+rentAlreadyRequested ถ้า BLOCKRENT)
        return result;
    }

    // ตรวจสิทธิ์เข้าถึงเอกสาร สำหรับ OCS launch
    // ลำดับ: 1)SecurePatient 2)หมอ 3)IPD 4)ActiveRent(อนุมัติแล้ว) 5)WorkRange
    // คืน Map: { accessStatus: OK|BLOCKSECURE|BLOCKRENT, rentAlreadyRequested?: boolean }
    private Map<String, Object> checkOcsAccess(String userId, String patId, String treatInDate, String treatClass) {
        String curDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));

        // ── ชั้น 1: SecurePatient ── คนไข้ขอปกปิด → block ทุกคน (รวมหมอ)
        if (repo.isSecurePatient(patId)) {
            return Map.of("accessStatus", "BLOCKSECURE");
        }

        // ── ชั้น 2: เป็นหมอ? ── หมอดูได้เลย
        if (repo.isDoctor(userId)) {
            return Map.of("accessStatus", "OK");
        }

        // ── ชั้น 3: IPD (CLASS='I') ── ไม่ใช่หมอ แต่เป็น IPD → ดูได้ทันที
        if ("I".equalsIgnoreCase(treatClass)) {
            return Map.of("accessStatus", "OK");
        }

        // ── ชั้น 4: มีสิทธิ์ยืมแฟ้มที่อนุมัติแล้ว (RENTYN='Y') ยังไม่หมดอายุ → ดูได้เสมอ (แม้เกิน workrange)
        if (repo.hasActiveRent(userId, patId, curDate)) {
            return Map.of("accessStatus", "OK");
        }

        // ── ชั้น 5: WorkRange ── INDATE + WRKRANGE วัน = Result
        //   ยังอยู่ในช่วง (today <= Result) → OK
        //   เกินช่วง                        → BLOCKRENT (ต้องขอยืมแฟ้ม)
        Integer workRange = repo.getWorkRange();
        if (workRange == null) {
            // ไม่เจอ config / null → default block (ตามที่ตกลง)
            return blockRent(userId, patId, curDate);
        }
        try {
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd");
            java.time.LocalDate inDate = java.time.LocalDate.parse(treatInDate, fmt);
            java.time.LocalDate result = inDate.plusDays(workRange);   // INDATE + WRKRANGE (ตัวเลขปกติ)
            java.time.LocalDate today  = java.time.LocalDate.now();
            if (today.isAfter(result)) {
                // เกิน work range → BLOCKRENT
                return blockRent(userId, patId, curDate);
            }
            // ยังอยู่ใน work range → ดูได้
            return Map.of("accessStatus", "OK");
        } catch (Exception e) {
            // parse INDATE ไม่ได้ → block ปลอดภัยไว้ก่อน
            log.warn("OCS workrange parse failed INDATE=[{}] WRKRANGE=[{}]: {}", treatInDate, workRange, e.getMessage());
            return blockRent(userId, patId, curDate);
        }
    }

    // helper: คืน BLOCKRENT พร้อมเช็คว่ามี request ค้างอยู่แล้วไหม
    private Map<String, Object> blockRent(String userId, String patId, String curDate) {
        boolean requested = repo.hasRentRequest(userId, patId, curDate);
        return Map.of("accessStatus", "BLOCKRENT", "rentAlreadyRequested", requested);
    }

    // ─── RENTT register ───────────────────────────────────────────

    public List<Map<String, Object>> getRentReasons() {
        return repo.getRentReasons();
    }

    public void registerRent(String userId, String clinCode, String patId,
                             String rentCode, String rentName,
                             String rentSdt, String rentEdt, String rentRemark) {
        repo.insertRent(userId, clinCode, patId, rentCode, rentName, rentSdt, rentEdt, rentRemark);
    }

    public void registerSecure(String userId, String patId, String memo) {
        repo.insertSecure(userId, patId, memo);
    }

    public boolean hasSecureRequest(String patId) {
        return repo.hasSecureRequest(patId);
    }

    // ─── Security admin ───────────────────────────────────────────

    public List<Map<String, Object>> searchSecure(String patId, String status, String dateFrom, String dateTo) {
        return repo.searchSecure(patId, status, dateFrom, dateTo);
    }

    // คืน true ถ้า update สำเร็จ (1 row), false ถ้าไม่มี row ตรงเงื่อนไข (เช่นถูก confirm ไปแล้ว)
    public boolean confirmSecure(String patId, String rowKey, String loginId) {
        return repo.confirmSecure(patId, rowKey, loginId) > 0;
    }

    public boolean endSecure(String patId, String rowKey, String loginId) {
        return repo.endSecure(patId, rowKey, loginId) > 0;
    }

    // ─── Rent admin ───────────────────────────────────────────────

    public List<Map<String, Object>> searchRent(String rentNo, String patId, String status,
                                                String dateFrom, String dateTo, String rentCode) {
        return repo.searchRent(rentNo, patId, status, dateFrom, dateTo, rentCode);
    }

    public boolean confirmRent(long rentNo) {
        return repo.confirmRent(rentNo) > 0;
    }

    // เทียบ IP กับ ranges (คั่นด้วย comma). รองรับ 3 แบบ:
    //   - CIDR        : 192.168.1.0/24
    //   - prefix      : 192.168.1.   (ลงท้ายด้วยจุด → startsWith)
    //   - exact       : 127.0.0.1
    boolean isIpAllowed(String ip, String ranges) {
        if (ip == null || ip.isBlank()) return false;
        if (ranges == null || ranges.isBlank()) return false;
        String clean = ip.trim();
        for (String rawRange : ranges.split(",")) {
            String range = rawRange.trim();
            if (range.isEmpty()) continue;
            try {
                if (range.contains("/")) {
                    if (cidrMatch(clean, range)) return true;
                } else if (range.endsWith(".")) {
                    if (clean.startsWith(range)) return true;
                } else {
                    if (clean.equals(range)) return true;
                }
            } catch (Exception e) {
                log.warn("Bad whitelist range [{}]: {}", range, e.getMessage());
            }
        }
        return false;
    }

    // CIDR match (IPv4) เช่น 192.168.1.50 อยู่ใน 192.168.1.0/24 ไหม
    private boolean cidrMatch(String ip, String cidr) {
        String[] parts = cidr.split("/");
        if (parts.length != 2) return false;
        long ipLong   = ipv4ToLong(ip);
        long netLong  = ipv4ToLong(parts[0]);
        int prefix    = Integer.parseInt(parts[1].trim());
        if (prefix < 0 || prefix > 32) return false;
        long mask = prefix == 0 ? 0L : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        return (ipLong & mask) == (netLong & mask);
    }

    private long ipv4ToLong(String ip) {
        String[] o = ip.trim().split("\\.");
        if (o.length != 4) throw new IllegalArgumentException("bad ipv4: " + ip);
        long r = 0;
        for (String s : o) {
            int v = Integer.parseInt(s.trim());
            if (v < 0 || v > 255) throw new IllegalArgumentException("bad octet: " + s);
            r = (r << 8) | v;
        }
        return r & 0xFFFFFFFFL;
    }

    // ─── PATIENTT ─────────────────────────────────────────────

    public String getHnSepConfig() { return repo.getHnSepConfig(); }

    public List<Map<String, Object>> searchPatient(String field, String keyword) {
        return repo.searchPatient(field, keyword);
    }

    // sync HIS เฉพาะ HN เดียวก่อนหาคนไข้ (ใช้ตอนพิมพ์ HN ในหน้า Scan/View)
    // ส่ง hn เข้า SP แบบ padded (raw, ไม่ trim) เพื่อให้คนไข้ใหม่จาก HIS sync แล้วเจอเลย
    public List<Map<String, Object>> syncAndFindPatient(String hn) {
        repo.interfaceByPatId(hn);
        return repo.findPatientByPatId(hn);
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


    // ─── Form Management ──────────────────────────────────────────

    public List<Map<String, Object>> searchForms(String field, String keyword) {
        return repo.searchForms(field, keyword);
    }

    public List<Map<String, Object>> getFormGroups() {
        return repo.getFormGroups();
    }

    public void insertForm(String formCode, String name, String grpCode, String active,
                           String ocrYn, String mediYn, String ocrPrint, int pageCount,
                           String followYn, String printYn) {
        repo.insertForm(formCode, name, grpCode, active, ocrYn, mediYn, ocrPrint, pageCount, followYn, printYn);
    }

    public void updateForm(String formCode, String name, String grpCode, String active,
                           String ocrYn, String mediYn, String ocrPrint, int pageCount,
                           String followYn, String printYn) {
        repo.updateForm(formCode, name, grpCode, active, ocrYn, mediYn, ocrPrint, pageCount, followYn, printYn);
    }

    public void deleteForm(String formCode) {
        repo.deleteForm(formCode);
    }

    // ─── OCR Return ─────────────────────────────────────────────

    public List<Map<String, Object>> getActiveClinics() {
        return repo.getActiveClinics();
    }

    public List<Map<String, Object>> getOcrReturnList(String startDate, String endDate,
                                                      String clinCode, String scanYn, String grpCode, String hn, String ocrPk) {
        return repo.getOcrReturnList(startDate, endDate, clinCode, scanYn, grpCode, hn, ocrPk);
    }

    public void updateOcrReturnMemo(String ocrPk, String bigo) {
        repo.updateOcrReturnMemo(ocrPk, bigo);
    }

    // ─── PrintNeed (request ขอพิมพ์) ──────────────────────────────

    // อ่าน AUTH ของ user จาก USERT (ใช้เช็คว่า auth='3' พิมพ์ได้เลยไหม)
    public String getUserAuth(String userId) {
        Map<String, Object> user = repo.findUserById(userId);
        if (user == null) return "";
        Object a = user.get("AUTH");
        return a == null ? "" : a.toString().trim();
    }

    // เตรียมข้อมูลตอนกด print: หา treatNo จาก pageNo + เช็คว่ามี request ค้างไหม
    // คืน { treatNo, patId, hasRequest }
    public Map<String, Object> checkPrintNeed(long pageNo) {
        Long treatNo = repo.findTreatNoByPageNo(pageNo);
        if (treatNo == null) {
            return Map.of("error", "ไม่พบ TREATNO ของ PAGENO นี้");
        }
        String patId = repo.findPatIdByTreatNo(treatNo);
        boolean has = repo.hasPrintNeed(treatNo, pageNo);
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("treatNo", treatNo);
        m.put("patId", patId == null ? "" : patId);
        m.put("hasRequest", has);
        return m;
    }

    public List<Map<String, Object>> getPrintReasons() {
        return repo.getPrintReasons();
    }

    public List<Map<String, Object>> getAllClinics() {
        return repo.getAllClinics();
    }

    // บันทึก request พิมพ์ — เช็คซ้ำอีกชั้นก่อน insert (กัน race)
    // คืน true=insert สำเร็จ, false=มี request ค้างแล้ว
    public boolean registerPrintNeed(long pageNo, String printCode, String cUserId, String needClin) {
        Long treatNo = repo.findTreatNoByPageNo(pageNo);
        if (treatNo == null) throw new RuntimeException("ไม่พบ TREATNO ของ PAGENO นี้");
        if (repo.hasPrintNeed(treatNo, pageNo)) return false;   // มีแล้ว
        String cDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        repo.insertPrintNeed(pageNo, printCode, cDate, cUserId, treatNo, needClin);
        return true;
    }

    // ─── PrintNeed admin ──────────────────────────────────────────

    public List<Map<String, Object>> searchPrintNeed(String patId, String patName,
                                                     String dateFrom, String dateTo,
                                                     String needClin, String printed) {
        return repo.searchPrintNeed(patId, patName, dateFrom, dateTo, needClin, printed);
    }

    // Confirm กดได้ตลอด → คืน true เสมอถ้า update โดน row
    public boolean confirmPrintNeed(long seq) {
        return repo.confirmPrintNeed(seq) > 0;
    }

    // Cancel เฉพาะ NEEDCANCLE='N' → false ถ้าถูก cancel ไปแล้ว
    public boolean cancelPrintNeed(long seq) {
        return repo.cancelPrintNeed(seq) > 0;
    }

}
