package com.emr.demo.controller;

import com.emr.demo.service.EmrService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    private final EmrService emrService;

    public ApiController(EmrService emrService) {
        this.emrService = emrService;
    }

    @GetMapping("/treatments")
    public ResponseEntity<?> getTreatments(@RequestParam String hn) {
        if (hn == null || hn.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "HN is required"));
        }
        try {
            return ResponseEntity.ok(emrService.getTreatments(hn));
        } catch (Exception e) {
            log.error("Error getting treatments for HN={}", hn, e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/chartpages/{treatNo}")
    public ResponseEntity<?> getChartPages(@PathVariable long treatNo) {
        try {
            return ResponseEntity.ok(emrService.getChartPages(treatNo));
        } catch (Exception e) {
            log.error("Error getting chartpages for TREATNO={}", treatNo, e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/image/{pageNo}")
    public ResponseEntity<byte[]> getImage(@PathVariable long pageNo,
                                           @RequestParam(defaultValue = "jpg") String ext) {
        try {
            byte[] bytes = emrService.getImageBytes(pageNo, ext);
            MediaType mediaType = resolveMediaType(ext);
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                    .body(bytes);
        } catch (IOException e) {
            log.warn("Image not found: pageNo={} ext={}", pageNo, ext);
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/scan/upload")
    public ResponseEntity<?> uploadScan(
            @RequestParam String hn,
            @RequestParam String formCode,
            @RequestParam(defaultValue = "000") String grpMid,
            @RequestParam(defaultValue = "DEMO") String userId,
            @RequestParam("file") MultipartFile file) {

        if (hn == null || hn.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "HN is required"));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is required"));
        }

        try {
            long pageNo = emrService.saveScan(hn, formCode, grpMid, file, userId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "pageNo", pageNo,
                    "message", "บันทึกสำเร็จ PAGENO=" + pageNo
            ));
        } catch (Exception e) {
            log.error("Error saving scan HN={} FORMCODE={}", hn, formCode, e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/forms")
    public ResponseEntity<?> getForms() {
        try {
            return ResponseEntity.ok(emrService.getForms());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Program Configuration ───────────────────────────────
    @GetMapping("/config")
    public ResponseEntity<?> getConfig(@RequestParam(defaultValue = "") String search) {
        try {
            return ResponseEntity.ok(emrService.getConfig(search));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/config/save")
    public ResponseEntity<?> saveConfig(@RequestBody java.util.List<java.util.Map<String, String>> items,
                                        @RequestParam(defaultValue = "DEMO") String userId) {
        try {
            emrService.saveConfig(items, userId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Detail Master ────────────────────────────────────────
    @GetMapping("/master/tabtyp")
    public ResponseEntity<?> getTabTyp() {
        try { return ResponseEntity.ok(emrService.getTabTyp()); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @GetMapping("/master/tabmst")
    public ResponseEntity<?> getTabMst(@RequestParam String tabCodTyp) {
        try { return ResponseEntity.ok(emrService.getTabMst(tabCodTyp)); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @GetMapping("/master/dtlmst")
    public ResponseEntity<?> getDtlMst(@RequestParam String dtlTblCod) {
        try { return ResponseEntity.ok(emrService.getDtlMst(dtlTblCod)); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @GetMapping("/master/dtsmst")
    public ResponseEntity<?> getDtsMst(@RequestParam String dtsTblCod, @RequestParam String dtsCod) {
        try { return ResponseEntity.ok(emrService.getDtsMst(dtsTblCod, dtsCod)); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    // ─── TabMst CRUD ──────────────────────────────────────────
    @PostMapping("/master/tabmst/insert")
    public ResponseEntity<?> insertTabMst(@RequestBody java.util.Map<String,String> b) {
        try { emrService.insertTabMst(b.get("tabCod"),b.get("tabCodNam"),b.get("tabCodTyp")); return ResponseEntity.ok(Map.of("success",true)); }
        catch(Exception e) { return ResponseEntity.status(500).body(Map.of("error",e.getMessage())); }
    }
    @PostMapping("/master/tabmst/update")
    public ResponseEntity<?> updateTabMst(@RequestBody java.util.Map<String,String> b) {
        try { emrService.updateTabMst(b.get("tabCod"),b.get("tabCodNam"),b.get("tabCodTyp")); return ResponseEntity.ok(Map.of("success",true)); }
        catch(Exception e) { return ResponseEntity.status(500).body(Map.of("error",e.getMessage())); }
    }
    @DeleteMapping("/master/tabmst/{tabCod}")
    public ResponseEntity<?> deleteTabMst(@PathVariable String tabCod) {
        try { emrService.deleteTabMst(tabCod); return ResponseEntity.ok(Map.of("success",true)); }
        catch(Exception e) { return ResponseEntity.status(500).body(Map.of("error",e.getMessage())); }
    }

    // ─── DtlMst CRUD ──────────────────────────────────────────
    @GetMapping("/master/dtlmst/nextseq")
    public ResponseEntity<?> getNextDtlSeq(@RequestParam String dtlTblCod) {
        try { return ResponseEntity.ok(Map.of("seq", emrService.getNextDtlDspSeq(dtlTblCod))); }
        catch(Exception e) { return ResponseEntity.status(500).body(Map.of("error",e.getMessage())); }
    }
    @PostMapping("/master/dtlmst/insert")
    public ResponseEntity<?> insertDtlMst(@RequestBody java.util.Map<String,String> b) {
        try {
            int seq = Integer.parseInt(b.getOrDefault("dtlDspSeq","1"));
            String uid = b.getOrDefault("userId","DEMO");
            emrService.insertDtlMst(b.get("dtlTblCod"),b.get("dtlCod"),b.get("dtlCodNam"),b.get("dtlCodVal"),seq,uid);
            return ResponseEntity.ok(Map.of("success",true));
        } catch(Exception e) { return ResponseEntity.status(500).body(Map.of("error",e.getMessage())); }
    }
    @PostMapping("/master/dtlmst/update")
    public ResponseEntity<?> updateDtlMst(@RequestBody java.util.Map<String,String> b) {
        try { emrService.updateDtlMst(b.get("dtlTblCod"),b.get("dtlCod"),b.get("dtlCodNam"),b.get("dtlCodVal"),b.getOrDefault("userId","DEMO")); return ResponseEntity.ok(Map.of("success",true)); }
        catch(Exception e) { return ResponseEntity.status(500).body(Map.of("error",e.getMessage())); }
    }
    @DeleteMapping("/master/dtlmst")
    public ResponseEntity<?> deleteDtlMst(@RequestParam String dtlTblCod, @RequestParam String dtlCod) {
        try { emrService.deleteDtlMst(dtlTblCod,dtlCod); return ResponseEntity.ok(Map.of("success",true)); }
        catch(Exception e) { return ResponseEntity.status(500).body(Map.of("error",e.getMessage())); }
    }
    @PostMapping("/master/dtlmst/reorder")
    public ResponseEntity<?> reorderDtlMst(@RequestParam String dtlTblCod,
                                            @RequestBody java.util.List<java.util.Map<String,String>> items) {
        try { emrService.reorderDtlMst(dtlTblCod,items); return ResponseEntity.ok(Map.of("success",true)); }
        catch(Exception e) { return ResponseEntity.status(500).body(Map.of("error",e.getMessage())); }
    }

    // ─── DtsMst CRUD ──────────────────────────────────────────
    @GetMapping("/master/dtsmst/nextseq")
    public ResponseEntity<?> getNextDtsSeq(@RequestParam String dtsTblCod, @RequestParam String dtsCod) {
        try { return ResponseEntity.ok(Map.of("seq", emrService.getNextDtsDspSeq(dtsTblCod,dtsCod))); }
        catch(Exception e) { return ResponseEntity.status(500).body(Map.of("error",e.getMessage())); }
    }
    @PostMapping("/master/dtsmst/insert")
    public ResponseEntity<?> insertDtsMst(@RequestBody java.util.Map<String,String> b) {
        try {
            int seq = Integer.parseInt(b.getOrDefault("dtsDspSeq","1"));
            String uid = b.getOrDefault("userId","DEMO");
            emrService.insertDtsMst(b.get("dtsTblCod"),b.get("dtsCod"),b.get("dtsSubCod"),b.get("dtsCodNam"),b.get("dtsCodVal"),seq,uid);
            return ResponseEntity.ok(Map.of("success",true));
        } catch(Exception e) { return ResponseEntity.status(500).body(Map.of("error",e.getMessage())); }
    }
    @PostMapping("/master/dtsmst/update")
    public ResponseEntity<?> updateDtsMst(@RequestBody java.util.Map<String,String> b) {
        try { emrService.updateDtsMst(b.get("dtsTblCod"),b.get("dtsCod"),b.get("dtsSubCod"),b.get("dtsCodNam"),b.get("dtsCodVal"),b.getOrDefault("userId","DEMO")); return ResponseEntity.ok(Map.of("success",true)); }
        catch(Exception e) { return ResponseEntity.status(500).body(Map.of("error",e.getMessage())); }
    }
    @DeleteMapping("/master/dtsmst")
    public ResponseEntity<?> deleteDtsMst(@RequestParam String dtsTblCod,@RequestParam String dtsCod,@RequestParam String dtsSubCod) {
        try { emrService.deleteDtsMst(dtsTblCod,dtsCod,dtsSubCod); return ResponseEntity.ok(Map.of("success",true)); }
        catch(Exception e) { return ResponseEntity.status(500).body(Map.of("error",e.getMessage())); }
    }
    @PostMapping("/master/dtsmst/reorder")
    public ResponseEntity<?> reorderDtsMst(@RequestParam String dtsTblCod, @RequestParam String dtsCod,
                                            @RequestBody java.util.List<java.util.Map<String,String>> items) {
        try { emrService.reorderDtsMst(dtsTblCod,dtsCod,items); return ResponseEntity.ok(Map.of("success",true)); }
        catch(Exception e) { return ResponseEntity.status(500).body(Map.of("error",e.getMessage())); }
    }

    // ─── Patient ──────────────────────────────────────────────
    @GetMapping("/patient/config")
    public ResponseEntity<?> getHnConfig() {
        try { return ResponseEntity.ok(Map.of("hnSep", emrService.getHnSepConfig())); }
        catch(Exception e) { return ResponseEntity.status(500).body(Map.of("error",e.getMessage())); }
    }

    @GetMapping("/patient/search")
    public ResponseEntity<?> searchPatient(@RequestParam(defaultValue="PATID") String field,
                                           @RequestParam String keyword) {
        if (keyword == null || keyword.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error","keyword required"));
        try { return ResponseEntity.ok(emrService.searchPatient(field, keyword)); }
        catch(Exception e) { return ResponseEntity.status(500).body(Map.of("error",e.getMessage())); }
    }

    @PostMapping("/patient/insert")
    public ResponseEntity<?> insertPatient(@RequestBody java.util.Map<String,String> b) {
        try {
            String patId = b.get("patId");
            if (patId == null || patId.isBlank())
                return ResponseEntity.badRequest().body(Map.of("error","patId required"));
            if (emrService.patientExists(patId.trim()))
                return ResponseEntity.badRequest().body(Map.of("error","HN นี้มีอยู่แล้ว"));
            emrService.insertPatient(patId, b.get("name"), b.get("sex"),
                    b.get("jumiNno"), b.get("birthDate"),
                    b.getOrDefault("userId","DEMO"));
            return ResponseEntity.ok(Map.of("success",true));
        } catch(Exception e) { return ResponseEntity.status(500).body(Map.of("error",e.getMessage())); }
    }

    private MediaType resolveMediaType(String ext) {
        return switch (ext.toLowerCase()) {
            case "tif", "tiff" -> MediaType.valueOf("image/tiff");
            case "png"         -> MediaType.IMAGE_PNG;
            case "pdf"         -> MediaType.APPLICATION_PDF;
            default            -> MediaType.IMAGE_JPEG;
        };
    }
}
