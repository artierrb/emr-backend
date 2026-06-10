package com.emr.demo.filter;

import com.emr.demo.util.JwtUtil;
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
        if (path.startsWith("/api/auth/") || path.startsWith("/api/image/") || path.startsWith("/api/ocrprint/pdf/") || path.startsWith("/api/scan/verify") || path.startsWith("/api/scan/complete") || !path.startsWith("/api/")) {
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
        req.setAttribute("userId", claims.getSubject());
        req.setAttribute("auth",   claims.get("auth", String.class));
        req.setAttribute("name",   claims.get("name", String.class));
        chain.doFilter(req, res);
    }
}
