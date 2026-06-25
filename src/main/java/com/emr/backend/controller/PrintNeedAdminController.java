package com.emr.backend.controller;

import com.emr.backend.service.EmrService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/printneed-admin")
public class PrintNeedAdminController {

    private final EmrService emrService;

    public PrintNeedAdminController(EmrService emrService) {
        this.emrService = emrService;
    }

    // list หน้า PrintNeed — filter: patId(HN), patName, dateFrom, dateTo, needClin, printed
    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam(required = false) String patId,
                                    @RequestParam(required = false) String patName,
                                    @RequestParam(required = false) String dateFrom,
                                    @RequestParam(required = false) String dateTo,
                                    @RequestParam(required = false, defaultValue = "ALL") String needClin,
                                    @RequestParam(required = false, defaultValue = "ALL") String printed) {
        try {
            List<Map<String, Object>> rows = emrService.searchPrintNeed(patId, patName, dateFrom, dateTo, needClin, printed);
            return ResponseEntity.ok(rows);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // Confirm — set PRINTED='Y', NEEDCNT+1 (กดได้ตลอด)
    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(@RequestBody Map<String, Object> b) {
        try {
            Object s = b.get("seq");
            if (s == null) return ResponseEntity.badRequest().body(Map.of("error", "seq จำเป็น"));
            long seq = Long.parseLong(s.toString());
            boolean ok = emrService.confirmPrintNeed(seq);
            if (!ok) return ResponseEntity.status(409).body(Map.of("error", "ไม่พบรายการ"));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // Cancel — set NEEDCANCLE='Y' (เฉพาะ NEEDCANCLE='N')
    @PostMapping("/cancel")
    public ResponseEntity<?> cancel(@RequestBody Map<String, Object> b) {
        try {
            Object s = b.get("seq");
            if (s == null) return ResponseEntity.badRequest().body(Map.of("error", "seq จำเป็น"));
            long seq = Long.parseLong(s.toString());
            boolean ok = emrService.cancelPrintNeed(seq);
            if (!ok) return ResponseEntity.status(409).body(Map.of("error", "ไม่สามารถ Cancel ได้ (อาจถูก cancel ไปแล้ว)"));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
