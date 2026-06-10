package com.emr.demo.controller;

import com.emr.demo.service.EmrService;
import com.emr.demo.util.JwtUtil;
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

    @GetMapping("/me")
    public ResponseEntity<?> me(jakarta.servlet.http.HttpServletRequest req) {
        String userId = (String) req.getAttribute("userId");
        String auth   = (String) req.getAttribute("auth");
        String name   = (String) req.getAttribute("name");
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return ResponseEntity.ok(Map.of("userId", userId, "auth", auth, "name", name));
    }
}
