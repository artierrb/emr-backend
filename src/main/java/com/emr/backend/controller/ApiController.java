package com.emr.backend.controller;

import com.emr.backend.service.EmrService;
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

    @GetMapping("/clinic/search")
    public ResponseEntity<?> searchClinic(@RequestParam(defaultValue="") String keyword) {
        try { return ResponseEntity.ok(emrService.searchClinic(keyword)); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @GetMapping("/user/search")
    public ResponseEntity<?> searchUser(@RequestParam(defaultValue="") String keyword) {
        try { return ResponseEntity.ok(emrService.searchUser(keyword)); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @PostMapping("/treatments/insert")
    public ResponseEntity<?> insertTreatment(@RequestBody Map<String,String> b) {
        try {
            long treatNo = emrService.insertTreatmentFull(
                b.get("patId"), b.get("inDate"), b.get("clinCode"),
                b.getOrDefault("docCode",""), b.get("classType"),
                b.getOrDefault("vstNum",""), b.getOrDefault("admNum",""),
                b.getOrDefault("userId","DEMO")
            );
            return ResponseEntity.ok(Map.of("success", true, "treatNo", treatNo));
        } catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @DeleteMapping("/treatments/{treatNo}")
    public ResponseEntity<?> deleteTreatment(@PathVariable long treatNo) {
        try { emrService.deleteTreatment(treatNo); return ResponseEntity.ok(Map.of("success", true)); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    // ─── OCR Print ────────────────────────────────────────────────

    @GetMapping("/ocrprint/setup")
    public ResponseEntity<?> getOcrPrintSetup(
            @RequestParam(defaultValue="ALL") String gubun,
            @RequestParam(defaultValue="") String clinCode,
            jakarta.servlet.http.HttpServletRequest req) {
        try {
            String userId = (String) req.getAttribute("userId");
            return ResponseEntity.ok(emrService.getOcrPrintSetup(gubun, userId, clinCode));
        } catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @GetMapping("/ocrprint/checkreprint")
    public ResponseEntity<?> checkReprint(@RequestParam String ocmNum, @RequestParam String formCode) {
        try { return ResponseEntity.ok(Map.of("reprinted", emrService.checkReprint(ocmNum, formCode))); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @GetMapping("/ocrprint/reasons")
    public ResponseEntity<?> getReprintReasons() {
        try { return ResponseEntity.ok(emrService.getReprintReasons()); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @GetMapping("/print/verify")
    public ResponseEntity<?> verifyPrintToken(@RequestParam String token) {
        try {
            Map<String,Object> info = emrService.verifyPrintToken(token);
            if (info == null) return ResponseEntity.status(401).body(Map.of("error","Invalid or expired token"));
            return ResponseEntity.ok(info);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/ocrprint/print")
    public ResponseEntity<?> printOcr(@RequestBody Map<String,String> b,
                                      jakarta.servlet.http.HttpServletRequest req) {
        try {
            String userId = (String) req.getAttribute("userId");
            String auth   = (String) req.getAttribute("auth");
            String name   = (String) req.getAttribute("name");

            Long treatNo = null;
            String treatNoStr = b.get("treatNo");
            if (treatNoStr != null && !treatNoStr.isBlank()) {
                try { treatNo = Long.parseLong(treatNoStr); } catch (Exception ignored) {}
            }

            // insert OCRPK + OCRPRINT (ที่ server) แล้วคืน ocrPk
            String ocrPk = emrService.printOcr(
                    b.get("ocmNum"), b.get("outDate"), b.get("formCode"),
                    userId, b.getOrDefault("reason",""), b.getOrDefault("patType","O"),
                    b.get("patId"), b.get("inDate"), b.get("clinCode"), b.get("docCode"),
                    treatNo
            );

            // ออก short-lived token ให้ EMRPrint.exe ที่ client เอาไป verify
            String token = emrService.generatePrintToken(userId, auth, name);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "ocrPk", ocrPk,
                    "token", token
            ));
        } catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @GetMapping("/ocrprint/pdf/{formCode}")
    public ResponseEntity<byte[]> getPdf(@PathVariable String formCode) {
        try {
            byte[] bytes = emrService.getPdfBytes(formCode);
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                    .header("Cache-Control", "no-cache")
                    .header("Content-Disposition", "inline; filename=\"" + formCode + ".pdf\"")
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ─── Viewer ───────────────────────────────────────────────────

    @GetMapping("/viewer/treatments")
    public ResponseEntity<?> getViewerTreatments(@RequestParam String hn) {
        try { return ResponseEntity.ok(emrService.getViewerTreatments(hn)); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @GetMapping("/viewer/pages")
    public ResponseEntity<?> getViewerPages(@RequestParam String hn, @RequestParam String formCode,
            @RequestParam(defaultValue="ALL") String classFilter) {
        try { return ResponseEntity.ok(emrService.getViewerPages(hn, formCode, classFilter)); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @GetMapping("/viewer/pages/treat")
    public ResponseEntity<?> getViewerPagesByTreat(@RequestParam Long treatNo, @RequestParam String formCode) {
        try { return ResponseEntity.ok(emrService.getViewerPagesByTreat(treatNo, formCode)); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @GetMapping("/treatments/full")
    public ResponseEntity<?> getTreatmentsFull(@RequestParam String hn) {
        try { return ResponseEntity.ok(emrService.getTreatmentsFull(hn)); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @PostMapping("/treatments/check")
    public ResponseEntity<?> updateTreatCheck(@RequestBody Map<String,String> b) {
        try {
            emrService.updateTreatCheck(
                Long.parseLong(b.get("treatNo")),
                Integer.parseInt(b.get("checkNo")),
                b.get("value"),
                b.getOrDefault("userId", "DEMO")
            );
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @GetMapping("/treatments/formcount")
    public ResponseEntity<?> getFormCount(@RequestParam Long treatNo) {
        try { return ResponseEntity.ok(emrService.getFormCountByTreatNo(treatNo)); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
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

    @PostMapping("/chartpages/move")
    public ResponseEntity<?> moveChartPages(@RequestBody java.util.Map<String, Object> b) {
        try {
            @SuppressWarnings("unchecked")
            java.util.List<Integer> pageNos = (java.util.List<Integer>) b.get("pageNos");
            String newFormCode = (String) b.get("newFormCode");
            if (pageNos == null || pageNos.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error","pageNos required"));
            if (newFormCode == null || newFormCode.isBlank()) return ResponseEntity.badRequest().body(Map.of("error","newFormCode required"));
            emrService.moveChartPages(pageNos.stream().map(Long::valueOf).toList(), newFormCode);
            return ResponseEntity.ok(Map.of("success", true, "moved", pageNos.size()));
        } catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
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
                                           @RequestParam(defaultValue = "jpg") String ext,
                                           @RequestParam(defaultValue = "0") String thumb,
                                           @RequestParam(defaultValue = "") String fmt,
                                           @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        boolean wantThumb = "1".equals(thumb) || "true".equalsIgnoreCase(thumb);
        boolean wantJpeg  = "jpg".equalsIgnoreCase(fmt) || "jpeg".equalsIgnoreCase(fmt);
        try {
            // ETag จาก lastModified+size ของไฟล์ต้นฉบับ (re-scan ทับ → เปลี่ยน → โหลดใหม่)
            // suffix แยก cache: -t (thumbnail), -j (full jpeg), ภาพเต็มต้นฉบับไม่มี suffix
            String sig = emrService.getImageSignature(pageNo, ext);
            String variant = wantThumb ? "-t" : (wantJpeg ? "-j" : "");
            String etag = "\"" + sig + variant + "\"";

            // ถ้า client cache ตรง → 304 ไม่ต้องอ่าน/แปลง/ส่ง byte ซ้ำ
            if (etag.equals(ifNoneMatch)) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .eTag(etag)
                        .cacheControl(org.springframework.http.CacheControl.noCache())
                        .build();
            }

            byte[] bytes;
            MediaType mediaType;
            if (wantThumb) {
                try {
                    bytes = emrService.getThumbnailBytes(pageNo, ext);
                    mediaType = MediaType.IMAGE_JPEG;   // thumbnail เป็น jpeg เสมอ
                } catch (Exception thumbErr) {
                    log.warn("Thumbnail failed pageNo={} ext={} → fallback full image: {}", pageNo, ext, thumbErr.getMessage());
                    bytes = emrService.getImageBytes(pageNo, ext);
                    mediaType = resolveMediaType(ext);
                }
            } else if (wantJpeg) {
                // viewer zoom — แปลงเป็น jpeg เต็มขนาด (รองรับ tiff ที่ Chrome แสดงไม่ได้)
                try {
                    bytes = emrService.getImageAsJpeg(pageNo, ext);
                    mediaType = MediaType.IMAGE_JPEG;
                } catch (Exception jpgErr) {
                    log.warn("JPEG convert failed pageNo={} ext={} → fallback original: {}", pageNo, ext, jpgErr.getMessage());
                    bytes = emrService.getImageBytes(pageNo, ext);
                    mediaType = resolveMediaType(ext);
                }
            } else {
                bytes = emrService.getImageBytes(pageNo, ext);
                mediaType = resolveMediaType(ext);
            }

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .eTag(etag)
                    // no-cache = ต้อง revalidate ทุกครั้ง แต่ถ้าไฟล์ไม่เปลี่ยน → 304 (ไม่โหลด byte ซ้ำ)
                    .cacheControl(org.springframework.http.CacheControl.noCache())
                    .body(bytes);
        } catch (IOException e) {
            log.warn("Image not found: pageNo={} ext={}", pageNo, ext);
            return ResponseEntity.notFound().build();
        }
    }

    // ─── Watermark ────────────────────────────────────────────────

    // config สำหรับ frontend — เปิด/ปิด + opacity
    @GetMapping("/watermark/config")
    public ResponseEntity<?> getWatermarkConfig() {
        try { return ResponseEntity.ok(emrService.getWatermarkConfig()); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    // ไฟล์ watermark — อ่านจาก server path (WTRMRKPTH), รองรับ jpg/png
    @GetMapping("/watermark/image")
    public ResponseEntity<byte[]> getWatermarkImage() {
        try {
            byte[] bytes = emrService.getWatermarkBytes();
            if (bytes == null) return ResponseEntity.notFound().build();
            MediaType mt = MediaType.valueOf(emrService.getWatermarkContentType());
            return ResponseEntity.ok()
                    .contentType(mt)
                    .cacheControl(org.springframework.http.CacheControl.noCache())
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/scan/upload")
    public ResponseEntity<?> uploadScan(
            @RequestParam String hn,
            @RequestParam String formCode,
            @RequestParam(defaultValue = "000") String grpMid,
            @RequestParam(defaultValue = "DEMO") String userId,
            @RequestParam(required = false) Long treatNo,
            @RequestParam("file") MultipartFile file) {

        if (hn == null || hn.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "HN is required"));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is required"));
        }

        try {
            long pageNo = emrService.saveScan(hn, formCode, grpMid, treatNo, file, userId);
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

    @GetMapping("/config/prgmode")
    public ResponseEntity<?> getPrgMode() {
        try { return ResponseEntity.ok(Map.of("prgMode", emrService.getPrgMode())); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
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

    // sync HIS เฉพาะ HN เดียวก่อนหาคนไข้ — ใช้ตอนพิมพ์ HN ในหน้า Scan/View
    // ห้าม trim hn — ต้องส่ง padded 8 หลักเข้า SP (เช่น '69     1' / '     123')
    @GetMapping("/patient/sync-find")
    public ResponseEntity<?> syncFindPatient(@RequestParam String hn) {
        if (hn == null || hn.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error","hn required"));
        try { return ResponseEntity.ok(emrService.syncAndFindPatient(hn)); }
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


    // ─── EMRScan Integration ──────────────────────────────────────────────────

    // GET /api/scan/token — generate short-lived token (5 min) for EMRScan.exe
    @GetMapping("/scan/token")
    public ResponseEntity<?> getScanToken(jakarta.servlet.http.HttpServletRequest req) {
        String userId = (String) req.getAttribute("userId");
        String auth   = (String) req.getAttribute("auth");
        String name   = (String) req.getAttribute("name");
        try {
            String token = emrService.generateScanToken(userId, auth, name);
            return ResponseEntity.ok(Map.of("token", token));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // POST /api/scan/complete — called by EMRScan.exe after confirm
    @PostMapping("/scan/complete")
    public ResponseEntity<?> scanComplete(@RequestBody Map<String,String> b) {
        try {
            String treatNo  = b.get("treatNo");
            String formCode = b.get("formCode");
            String userId   = b.get("userId");
            emrService.notifyScanComplete(treatNo, formCode, userId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/scan/status — polling endpoint for web frontend
    @GetMapping("/scan/status")
    public ResponseEntity<?> getScanStatus(
            @RequestParam(required=false) String treatNo,
            @RequestParam(required=false) String formCode) {
        try {
            return ResponseEntity.ok(emrService.getScanStatus(treatNo, formCode));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/scan/verify — called by EMRScan.exe to validate short-lived token
    @GetMapping("/scan/verify")
    public ResponseEntity<?> verifyScanToken(@RequestParam String token) {
        try {
            Map<String,Object> info = emrService.verifyScanToken(token);
            if (info == null) return ResponseEntity.status(401).body(Map.of("error","Invalid or expired token"));
            return ResponseEntity.ok(info);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Form Management ─────────────────────────────────────────

    @GetMapping("/forms/manage")
    public ResponseEntity<?> searchForms(
            @RequestParam(defaultValue="FORMCODE") String field,
            @RequestParam(defaultValue="") String keyword) {
        try { return ResponseEntity.ok(emrService.searchForms(field, keyword)); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @GetMapping("/forms/groups")
    public ResponseEntity<?> getFormGroups() {
        try { return ResponseEntity.ok(emrService.getFormGroups()); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @PostMapping("/forms/insert")
    public ResponseEntity<?> insertForm(@RequestBody Map<String,Object> b) {
        try {
            emrService.insertForm(
                    (String)b.get("FORMCODE"), (String)b.get("NAME"),
                    b.getOrDefault("GRPCODE","").toString(),
                    b.getOrDefault("ACTIVE","1").toString(),
                    b.getOrDefault("OCRYN","N").toString(),
                    b.getOrDefault("MEDIYN","N").toString(),
                    b.getOrDefault("OCRPRINT","N").toString(),
                    b.get("PAGECOUNT") != null ? Integer.parseInt(b.get("PAGECOUNT").toString()) : 1,
                    b.getOrDefault("FOLLOWYN","N").toString(),
                    b.getOrDefault("PRINTYN","N").toString()
            );
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @PostMapping("/forms/update")
    public ResponseEntity<?> updateForm(@RequestBody Map<String,Object> b) {
        try {
            emrService.updateForm(
                    (String)b.get("FORMCODE"), (String)b.get("NAME"),
                    b.getOrDefault("GRPCODE","").toString(),
                    b.getOrDefault("ACTIVE","1").toString(),
                    b.getOrDefault("OCRYN","N").toString(),
                    b.getOrDefault("MEDIYN","N").toString(),
                    b.getOrDefault("OCRPRINT","N").toString(),
                    b.get("PAGECOUNT") != null ? Integer.parseInt(b.get("PAGECOUNT").toString()) : 1,
                    b.getOrDefault("FOLLOWYN","N").toString(),
                    b.getOrDefault("PRINTYN","N").toString()
            );
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @DeleteMapping("/forms/{formCode}")
    public ResponseEntity<?> deleteForm(@PathVariable String formCode) {
        try { emrService.deleteForm(formCode); return ResponseEntity.ok(Map.of("success", true)); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    // ─── OCR Return ───────────────────────────────────────────────

    @GetMapping("/ocrreturn/clinics")
    public ResponseEntity<?> getOcrReturnClinics() {
        try { return ResponseEntity.ok(emrService.getActiveClinics()); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @GetMapping("/ocrreturn/list")
    public ResponseEntity<?> getOcrReturnList(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue="ALL") String clinCode,
            @RequestParam(defaultValue="ALL") String scanYn,
            @RequestParam(defaultValue="ALL") String grpCode,
            @RequestParam(defaultValue="") String hn,
            @RequestParam(defaultValue="") String ocrPk) {
        try {
            return ResponseEntity.ok(emrService.getOcrReturnList(startDate, endDate, clinCode, scanYn, grpCode, hn, ocrPk));
        } catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @PostMapping("/ocrreturn/memo")
    public ResponseEntity<?> saveOcrReturnMemo(@RequestBody Map<String,String> b) {
        try {
            emrService.updateOcrReturnMemo(b.get("ocrPk"), b.getOrDefault("bigo",""));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
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
