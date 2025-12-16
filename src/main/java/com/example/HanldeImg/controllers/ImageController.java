package com.example.HanldeImg.controllers;

import com.example.HanldeImg.services.image_service.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ImageController {

    private final ImageService imageService;

    @PostMapping(value = "/loadImages", consumes = "multipart/form-data")
    public ResponseEntity<?> loadImages(
            @RequestPart("files") List<MultipartFile> files,
            @RequestPart("projectName") String projectName) {
            imageService.uploadProject(files, projectName);
            return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/updateImages", consumes = "multipart/form-data")
    public ResponseEntity<?> updateImages(
            @RequestPart("files") List<MultipartFile> files,
            @RequestPart("projectName") String projectName){

        imageService.updateProject(files, projectName);
        return ResponseEntity.ok().build();
    }
}
