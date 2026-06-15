package com.emr.backend.controller;

import com.emr.backend.service.EmrService;
import com.emr.backend.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final EmrService emrService;
    private final JwtUtil jwtUtil;

    public AuthController(EmrService emrService, JwtUtil jwtUtil) {
        this.emrService = emrService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String userId = body.get("userId");
        String password = body.get("password");
        if (userId == null || password == null)
            return ResponseEntity.badRequest().body(Map.of("error", "userId and password required"));

        Map<String, Object> user = emrService.login(userId.trim(), password);
        if (user == null)
            return ResponseEntity.status(401).body(Map.of("error", "รหัสผู้ใช้หรือรหัสผ่านไม่ถูกต้อง หรือบัญชีหมดอายุ"));

        String auth = user.getOrDefault("AUTH", "0").toString().trim();
        String name = user.getOrDefault("NAME", userId).toString().trim();
        String token = jwtUtil.generate(userId.trim(), auth, name);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "userId", userId.trim(),
                "name", name,
                "auth", auth
        ));
    }

    // OCS launch — เปิดจากโปรแกรม OCS (VB6) ผ่าน URL พร้อม param
    // backend verify ทุกอย่าง (IP whitelist + user + PATID/OCMNUM) แล้วออก token แบบ view-only
    // คืน token + ข้อมูล user + treat object เต็ม (มี treatNo) ให้ frontend fill viewer ข้าม modal
    @PostMapping("/ocs-launch")
    public ResponseEntity<?> ocsLaunch(@RequestBody Map<String, String> body,
                                       jakarta.servlet.http.HttpServletRequest req) {
        String patId    = body.get("patId");
        String userId   = body.get("userId");
        String clinCode = body.get("clinCode");
        String inDate   = body.get("inDate");
        String ocmNum   = body.get("ocmNum");

        if (patId == null || patId.isBlank())   return ResponseEntity.badRequest().body(Map.of("error", "patId required"));
        if (userId == null || userId.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "userId required"));
        if (ocmNum == null || ocmNum.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "ocmNum required"));

        String clientIp = resolveClientIp(req);

        try {
            Map<String, Object> result = emrService.ocsLaunch(patId, userId, clinCode, inDate, ocmNum, clientIp);
            String uid  = result.get("userId").toString();
            String name = result.get("name").toString();
            String token = jwtUtil.generateOcs(uid, name);

            Map<String, Object> resp = new java.util.HashMap<>();
            resp.put("token",  token);
            resp.put("userId", uid);
            resp.put("name",   name);
            resp.put("auth",   "0");       // view-only
            resp.put("ocs",    true);
            resp.put("treat",  result.get("treat"));
            resp.put("clinCode", clinCode == null ? "" : clinCode.trim());   // ส่งกลับไว้ใช้ตอนลงทะเบียนยืมแฟ้ม
            resp.put("accessStatus", result.getOrDefault("accessStatus", "OK"));
            if (result.containsKey("rentAlreadyRequested"))
                resp.put("rentAlreadyRequested", result.get("rentAlreadyRequested"));
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    // หา client IP จริง — รองรับทั้งมี/ไม่มี reverse proxy (nginx)
    // ถ้ามี X-Forwarded-For ใช้ค่าแรก (client เดิม), ไม่งั้น fallback getRemoteAddr()
    private String resolveClientIp(jakarta.servlet.http.HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For: client, proxy1, proxy2 → เอาตัวแรก
            String first = xff.split(",")[0].trim();
            if (!first.isEmpty()) return normalizeIp(first);
        }
        String xr = req.getHeader("X-Real-IP");
        if (xr != null && !xr.isBlank()) return normalizeIp(xr.trim());
        return normalizeIp(req.getRemoteAddr());
    }

    // IPv6 localhost (::1) และ IPv4-mapped (::ffff:192.168.1.1) → แปลงเป็น IPv4 ปกติ
    private String normalizeIp(String ip) {
        if (ip == null) return "";
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) return "127.0.0.1";
        if (ip.startsWith("::ffff:")) return ip.substring("::ffff:".length());
        return ip;
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(jakarta.servlet.http.HttpServletRequest req) {
        String userId = (String) req.getAttribute("userId");
        String auth   = (String) req.getAttribute("auth");
        String name   = (String) req.getAttribute("name");
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return ResponseEntity.ok(Map.of("userId", userId, "auth", auth, "name", name));
    }
}