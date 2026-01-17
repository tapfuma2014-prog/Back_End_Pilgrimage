package com.pilgrimage.backend.dto;

public class VisitPageDto {
    private final String label;
    private final String page;
    private final String section;

    public VisitPageDto(String label, String page, String section) {
        this.label = label;
        this.page = page;
        this.section = section;
    }

    public String getLabel() {
        return label;
    }

    public String getPage() {
        return page;
    }

    public String getSection() {
        return section;
    }
}
