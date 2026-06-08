package com.emr.demo.controller;

import com.emr.demo.service.EmrService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    private final EmrService emrService;

    public PageController(EmrService emrService) {
        this.emrService = emrService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("forms", emrService.getForms());
        return "index";
    }
}
