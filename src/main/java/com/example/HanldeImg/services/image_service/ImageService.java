package com.example.HanldeImg.services.image_service;

import com.example.HanldeImg.entity.dto.ImageDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ImageService {
    void uploadProject(List<MultipartFile> files, String dirName);
    void updateProject(List<MultipartFile> files, String dirName);
}
