package com.emr.backend.controller;

import com.emr.backend.service.EmrService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final EmrService emrService;

    public UserController(EmrService emrService) {
        this.emrService = emrService;
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam(defaultValue = "USERID") String field,
            @RequestParam(defaultValue = "") String keyword) {
        try { return ResponseEntity.ok(emrService.searchUsers(field, keyword)); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    // paginated list (server-side) — โหลดทีละหน้าเหมือน PatientSearch
    // page เริ่มที่ 0, size = จำนวนต่อหน้า; รองรับ field/keyword เดียวกับ search
    @GetMapping("/list")
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "USERID") String field,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        try { return ResponseEntity.ok(emrService.listUsers(field, keyword, page, size)); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    // เช็ค USERID ซ้ำ — frontend เรียกตอน blur / ก่อนบันทึก
    @GetMapping("/exists")
    public ResponseEntity<?> exists(@RequestParam String userId) {
        try { return ResponseEntity.ok(Map.of("exists", emrService.userExists(userId))); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @PostMapping("/insert")
    public ResponseEntity<?> insert(@RequestBody Map<String, String> b) {
        try {
            emrService.insertUser(b.get("userId"), b.get("password"),
                    b.get("name"), b.getOrDefault("auth", "0"),
                    b.getOrDefault("clinCode", ""), b.getOrDefault("edate", ""));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalStateException e) {
            // USERID ซ้ำ (กันชั้นสุดท้ายจาก service) — คืน 409 ให้ frontend แยกกรณีได้
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @PostMapping("/update")
    public ResponseEntity<?> update(@RequestBody Map<String, String> b) {
        try {
            emrService.updateUser(b.get("userId"), b.get("name"),
                    b.getOrDefault("auth", "0"),
                    b.getOrDefault("clinCode", ""), b.getOrDefault("edate", ""));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @PostMapping("/password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> b) {
        try {
            emrService.updateUserPassword(b.get("userId"), b.get("password"));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<?> delete(@PathVariable String userId) {
        try { emrService.deleteUser(userId); return ResponseEntity.ok(Map.of("success", true)); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @PostMapping("/interface")
    public ResponseEntity<?> userInterface() {
        try { emrService.userInterfaceFromHis(); return ResponseEntity.ok(Map.of("success", true)); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }
}
