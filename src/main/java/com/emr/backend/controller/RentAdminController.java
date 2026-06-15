package com.emr.backend.controller;

import com.emr.backend.service.EmrService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rent-admin")
public class RentAdminController {

    private final EmrService emrService;

    public RentAdminController(EmrService emrService) {
        this.emrService = emrService;
    }

    // list หน้า ScanRentView — filter: rentNo, patId, status (ALL/WAITING/CONFIRMED), dateFrom, dateTo, rentCode
    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam(required = false) String rentNo,
                                    @RequestParam(required = false) String patId,
                                    @RequestParam(required = false, defaultValue = "ALL") String status,
                                    @RequestParam(required = false) String dateFrom,
                                    @RequestParam(required = false) String dateTo,
                                    @RequestParam(required = false) String rentCode) {
        try {
            List<Map<String, Object>> rows = emrService.searchRent(rentNo, patId, status, dateFrom, dateTo, rentCode);
            return ResponseEntity.ok(rows);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // Confirm — set RENTYN='Y', AGREENO=Max+1, BANNABDATE=now (เฉพาะ RENTYN='N')
    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(@RequestBody Map<String, Object> b) {
        try {
            Object rn = b.get("rentNo");
            if (rn == null) return ResponseEntity.badRequest().body(Map.of("error", "rentNo จำเป็น"));
            long rentNo = Long.parseLong(rn.toString());

            boolean ok = emrService.confirmRent(rentNo);
            if (!ok) return ResponseEntity.status(409).body(Map.of("error", "ไม่สามารถ Confirm ได้ (อาจถูกดำเนินการไปแล้ว)"));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
