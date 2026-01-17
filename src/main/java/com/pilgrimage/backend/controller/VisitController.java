package com.pilgrimage.backend.controller;

import com.pilgrimage.backend.dto.VisitPageDto;
import com.pilgrimage.backend.service.VisitService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/visit")
public class VisitController {

    private final VisitService visitService;

    public VisitController(VisitService visitService) {
        this.visitService = visitService;
    }

    @GetMapping("/pages")
    public List<VisitPageDto> getVisitPages() {
        return visitService.getVisitPages();
    }
}
