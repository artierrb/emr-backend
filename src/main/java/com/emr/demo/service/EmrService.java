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

    public EmrService(EmrRepository repo, FileService fileService) {
        this.repo = repo;
        this.fileService = fileService;
    }

    // ─── Viewer ───────────────────────────────────────────────

    public List<Treatment> getTreatments(String hn) {
        return repo.getTreatmentsByPatId(hn.trim().toUpperCase());
    }

    public List<ChartPage> getChartPages(long treatNo) {
        List<ChartPage> pages = repo.getChartPagesByTreatNo(treatNo);
        for (ChartPage cp : pages) {
            cp.setFilePath("/api/image/" + cp.getPageNo() +
                    "?ext=" + (cp.getExtension() != null ? cp.getExtension() : "jpg"));
        }
        return pages;
    }

    public List<Form> getForms() { return repo.getActiveForms(); }

    // ─── Scan ─────────────────────────────────────────────────

    @Transactional
    public long saveScan(String hn, String formCode, String grpMid,
                         MultipartFile file, String userId) throws IOException {
        PathConfig pathConfig = repo.getActivePath();
        String today = fileService.getTodayString();
        String ext = fileService.getExtension(file.getOriginalFilename());

        Long treatNo = repo.findTreatNo(hn, today);
        if (treatNo == null) {
            treatNo = repo.insertTreatment(hn, today, "SCAN");
            log.info("Created new TREATNO={} for HN={}", treatNo, hn);
        }

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

    // ─── Program Config ───────────────────────────────────────

    public List<Map<String, Object>> getConfig(String search) { return repo.getConfig(search); }

    public void saveConfig(List<Map<String, String>> items, String userId) {
        for (Map<String, String> item : items) {
            repo.saveConfigItem(item.get("dtlCod"), item.get("dtlCodVal"), userId);
        }
    }

    // ─── Detail Master queries ────────────────────────────────

    public List<Map<String, Object>> getTabTyp()                          { return repo.getTabTyp(); }
    public List<Map<String, Object>> getTabMst(String t)                  { return repo.getTabMst(t); }
    public List<Map<String, Object>> getDtlMst(String t)                  { return repo.getDtlMst(t); }
    public List<Map<String, Object>> getDtsMst(String t, String c)        { return repo.getDtsMst(t, c); }

    // ─── TabMst CRUD ──────────────────────────────────────────

    public void insertTabMst(String tabCod, String tabCodNam, String tabCodTyp) { repo.insertTabMst(tabCod, tabCodNam, tabCodTyp); }
    public void updateTabMst(String tabCod, String tabCodNam, String tabCodTyp) { repo.updateTabMst(tabCod, tabCodNam, tabCodTyp); }
    public void deleteTabMst(String tabCod)                                      { repo.deleteTabMst(tabCod); }

    // ─── DtlMst CRUD ──────────────────────────────────────────

    public int getNextDtlDspSeq(String dtlTblCod) { return repo.getNextDtlDspSeq(dtlTblCod); }

    public void insertDtlMst(String dtlTblCod, String dtlCod, String dtlCodNam, String dtlCodVal, int dtlDspSeq, String userId) {
        repo.insertDtlMst(dtlTblCod, dtlCod, dtlCodNam, dtlCodVal, dtlDspSeq, userId);
    }

    public void updateDtlMst(String dtlTblCod, String dtlCod, String dtlCodNam, String dtlCodVal, String userId) {
        repo.updateDtlMst(dtlTblCod, dtlCod, dtlCodNam, dtlCodVal, userId);
    }

    public void deleteDtlMst(String dtlTblCod, String dtlCod) { repo.deleteDtlMst(dtlTblCod, dtlCod); }

    public void reorderDtlMst(String dtlTblCod, List<Map<String, String>> items) {
        for (int i = 0; i < items.size(); i++) {
            repo.updateDtlDspSeq(dtlTblCod, items.get(i).get("dtlCod"), i + 1);
        }
    }

    // ─── DtsMst CRUD ──────────────────────────────────────────

    public int getNextDtsDspSeq(String dtsTblCod, String dtsCod) { return repo.getNextDtsDspSeq(dtsTblCod, dtsCod); }

    public void insertDtsMst(String dtsTblCod, String dtsCod, String dtsSubCod,
                              String dtsCodNam, String dtsCodVal, int dtsDspSeq, String userId) {
        repo.insertDtsMst(dtsTblCod, dtsCod, dtsSubCod, dtsCodNam, dtsCodVal, dtsDspSeq, userId);
    }

    public void updateDtsMst(String dtsTblCod, String dtsCod, String dtsSubCod,
                              String dtsCodNam, String dtsCodVal, String userId) {
        repo.updateDtsMst(dtsTblCod, dtsCod, dtsSubCod, dtsCodNam, dtsCodVal, userId);
    }

    public void deleteDtsMst(String dtsTblCod, String dtsCod, String dtsSubCod) {
        repo.deleteDtsMst(dtsTblCod, dtsCod, dtsSubCod);
    }

    public void reorderDtsMst(String dtsTblCod, String dtsCod, List<Map<String, String>> items) {
        for (int i = 0; i < items.size(); i++) {
            repo.updateDtsDspSeq(dtsTblCod, dtsCod, items.get(i).get("dtsSubCod"), i + 1);
        }
    }

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
            int birthYear = Integer.parseInt(birthDate.substring(0, 4));
            return java.time.LocalDate.now().getYear() - birthYear;
        } catch (Exception e) { return 0; }
    }
}
