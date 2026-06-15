package com.emr.backend.controller;

import com.emr.backend.service.EmrService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rent")
public class RentController {

    private final EmrService emrService;

    public RentController(EmrService emrService) {
        this.emrService = emrService;
    }

    // dropdown เหตุผลการยืมแฟ้ม (RENTRSN)
    @GetMapping("/reasons")
    public ResponseEntity<?> getRentReasons() {
        try { return ResponseEntity.ok(emrService.getRentReasons()); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    // ลงทะเบียนขอยืมแฟ้ม — เซฟลง RENTT
    // userId/clinCode/patId มาจาก OCS (frontend fill auto), rentCode/rentName จาก dropdown
    @PostMapping("/register")
    public ResponseEntity<?> registerRent(@RequestBody Map<String, String> b,
                                          jakarta.servlet.http.HttpServletRequest req) {
        try {
            // ใช้ userId จาก token (source of truth) ไม่ใช่จาก body — กันปลอม
            String tokenUserId = (String) req.getAttribute("userId");
            String userId = (tokenUserId != null && !tokenUserId.isBlank())
                    ? tokenUserId : b.getOrDefault("userId", "");

            String clinCode   = b.getOrDefault("clinCode", "");
            String patId      = b.getOrDefault("patId", "");
            String rentCode   = b.getOrDefault("rentCode", "");
            String rentName   = b.getOrDefault("rentName", "");
            String rentSdt    = b.getOrDefault("rentSdt", "");
            String rentEdt    = b.getOrDefault("rentEdt", "");
            String rentRemark = b.getOrDefault("rentRemark", "");

            if (userId.isBlank() || patId.isBlank())
                return ResponseEntity.badRequest().body(Map.of("error", "userId และ patId จำเป็น"));
            if (rentCode.isBlank())
                return ResponseEntity.badRequest().body(Map.of("error", "กรุณาเลือกเหตุผลในการยืมแฟ้ม"));
            if (rentSdt.isBlank() || rentEdt.isBlank())
                return ResponseEntity.badRequest().body(Map.of("error", "กรุณาระบุวันที่"));

            emrService.registerRent(userId, clinCode, patId, rentCode, rentName, rentSdt, rentEdt, rentRemark);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // เช็คว่ามี request ปกปิดค้างอยู่แล้วไหม (เรียกตอนกดปุ่ม SECU ก่อนเปิด modal)
    @GetMapping("/secure/check")
    public ResponseEntity<?> checkSecure(@RequestParam String patId) {
        try {
            return ResponseEntity.ok(Map.of("exists", emrService.hasSecureRequest(patId)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ลงทะเบียนปกปิดข้อมูลผู้ป่วย — เซฟลง USERSECUSECRET
    @PostMapping("/secure")
    public ResponseEntity<?> registerSecure(@RequestBody Map<String, String> b,
                                            jakarta.servlet.http.HttpServletRequest req) {
        try {
            // ใช้ userId จาก token (source of truth) ไม่ใช่จาก body — กันปลอม
            String tokenUserId = (String) req.getAttribute("userId");
            String userId = (tokenUserId != null && !tokenUserId.isBlank())
                    ? tokenUserId : b.getOrDefault("userId", "");

            String patId = b.getOrDefault("patId", "");
            String memo  = b.getOrDefault("memo", "");

            if (userId.isBlank() || patId.isBlank())
                return ResponseEntity.badRequest().body(Map.of("error", "userId และ patId จำเป็น"));

            // กันลงซ้ำ — เช็คอีกชั้นที่ backend (เผื่อมีการ request พร้อมกัน)
            if (emrService.hasSecureRequest(patId))
                return ResponseEntity.status(409).body(Map.of("error", "มีการลงทะเบียนปกปิดข้อมูลผู้ป่วยเรียบร้อยแล้ว กรุณาติดต่อแอดมิน"));

            emrService.registerSecure(userId, patId, memo);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}