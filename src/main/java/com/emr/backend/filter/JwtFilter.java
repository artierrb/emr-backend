package com.emr.backend.filter;

import com.emr.backend.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {

        String path = req.getRequestURI();
        // skip auth endpoints, image endpoint, and static resources
        if (path.startsWith("/api/auth/") || path.startsWith("/api/image/") || path.startsWith("/api/ocrprint/pdf/") || path.startsWith("/api/scan/verify") || path.startsWith("/api/scan/complete") || path.startsWith("/api/print/verify") || !path.startsWith("/api/")) {
            chain.doFilter(req, res); return;
        }

        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            res.setStatus(401);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Unauthorized\"}");
            return;
        }

        String token = header.substring(7);
        if (!jwtUtil.isValid(token)) {
            res.setStatus(401);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Token expired or invalid\"}");
            return;
        }

        Claims claims = jwtUtil.parse(token);

        // OCS token = view-only. บล็อกไม่ให้เรียก scan endpoint ที่ backend (defense layer 2)
        // กันกรณีปลอม request ตรงไปที่ API แม้ frontend จะ lock UI ไว้แล้ว
        Boolean ocs = claims.get("ocs", Boolean.class);
        boolean isOcs = ocs != null && ocs;
        if (isOcs && isScanEndpoint(path)) {
            res.setStatus(403);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Forbidden in OCS mode\"}");
            return;
        }

        req.setAttribute("userId", claims.getSubject());
        req.setAttribute("auth",   claims.get("auth", String.class));
        req.setAttribute("name",   claims.get("name", String.class));
        req.setAttribute("ocs",    isOcs);
        chain.doFilter(req, res);
    }

    // scan-related endpoints ที่ OCS mode ห้ามเรียก
    // viewer / ocrprint / image / chartpages / treatments(read) ปล่อยผ่าน เพราะ OCS = viewer ใช้ได้
    private boolean isScanEndpoint(String path) {
        return path.startsWith("/api/scan/upload")
                || path.startsWith("/api/scan/token")
                || path.startsWith("/api/scan/status")
                || path.startsWith("/api/chartpages/move")
                || path.startsWith("/api/treatments/insert")
                || path.startsWith("/api/treatments/check")
                || path.startsWith("/api/treatments/") && path.matches("/api/treatments/\\d+");  // DELETE /treatments/{id}
    }
}