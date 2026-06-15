package com.emr.backend.controller;

import com.emr.backend.service.EmrService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/secure-admin")
public class SecurityAdminController {

    private final EmrService emrService;

    public SecurityAdminController(EmrService emrService) {
        this.emrService = emrService;
    }

    // list หน้า ScanSecurityView — filter: patId, status (ALL/WAITING/CONFIRMED/END), dateFrom, dateTo
    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam(required = false) String patId,
                                    @RequestParam(required = false, defaultValue = "ALL") String status,
                                    @RequestParam(required = false) String dateFrom,
                                    @RequestParam(required = false) String dateTo) {
        try {
            List<Map<String, Object>> rows = emrService.searchSecure(patId, status, dateFrom, dateTo);
            return ResponseEntity.ok(rows);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // Confirm — set SECUAGREE='Y' (เฉพาะ row ที่ยัง 'N')
    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(@RequestBody Map<String, String> b,
                                     jakarta.servlet.http.HttpServletRequest req) {
        try {
            String loginId = (String) req.getAttribute("userId");
            String patId   = b.getOrDefault("patId", "");
            String rowKey  = b.getOrDefault("rowKey", "");   // SECUDATE string (yyyy-MM-dd HH:mm:ss.mmm)
            if (patId.isBlank() || rowKey.isBlank())
                return ResponseEntity.badRequest().body(Map.of("error", "patId และ rowKey จำเป็น"));

            boolean ok = emrService.confirmSecure(patId, rowKey, loginId);
            if (!ok) return ResponseEntity.status(409).body(Map.of("error", "ไม่สามารถ Confirm ได้ (อาจถูกดำเนินการไปแล้ว)"));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // End — set SECUENDYN='Y' (เฉพาะ row ที่ SECUAGREE='Y' AND SECUENDYN='N')
    @PostMapping("/end")
    public ResponseEntity<?> end(@RequestBody Map<String, String> b,
                                 jakarta.servlet.http.HttpServletRequest req) {
        try {
            String loginId = (String) req.getAttribute("userId");
            String patId   = b.getOrDefault("patId", "");
            String rowKey  = b.getOrDefault("rowKey", "");
            if (patId.isBlank() || rowKey.isBlank())
                return ResponseEntity.badRequest().body(Map.of("error", "patId และ rowKey จำเป็น"));

            boolean ok = emrService.endSecure(patId, rowKey, loginId);
            if (!ok) return ResponseEntity.status(409).body(Map.of("error", "ไม่สามารถ End ได้ (อาจถูกดำเนินการไปแล้ว)"));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
