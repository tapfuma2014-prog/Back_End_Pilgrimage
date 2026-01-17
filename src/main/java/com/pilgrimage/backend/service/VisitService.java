package com.pilgrimage.backend.service;

import com.pilgrimage.backend.dto.VisitPageDto;

import java.util.List;

public interface VisitService {
    List<VisitPageDto> getVisitPages();
}
