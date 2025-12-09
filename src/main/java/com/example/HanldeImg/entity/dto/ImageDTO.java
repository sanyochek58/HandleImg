package com.example.HanldeImg.entity.dto;

import com.example.HanldeImg.entity.model.Status;

import java.time.LocalDate;

public record ImageDTO(
        String name, LocalDate createdDate, String status
) {
}
