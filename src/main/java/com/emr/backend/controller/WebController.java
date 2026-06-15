package com.emr.backend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    // ส่งทุก route ที่ไม่ใช่ /api และไม่ใช่ static file กลับไป index.html
    // ให้ Vue router จัดการ client-side routing เอง (แก้ปัญหา refresh แล้ว 404)
    @GetMapping(value = {
            "/scan", "/scan/**",
            "/viewer", "/viewer/**",
            "/login"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}