package com.example.HanldeImg.controllers;

import com.example.HanldeImg.entity.dto.ImageDTO;
import com.example.HanldeImg.services.image_service.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ImageController {

    private final ImageService imageService;

    @PostMapping(value = "/loadImages", consumes = "multipart/form-data")
    public ResponseEntity<?> loadImages(
            @RequestPart("files") List<MultipartFile> files,
            @RequestPart("projectName") String projectName,
            @RequestPart("imageDTO") String imageDTOJson
    ) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<ImageDTO> imageDTOs = Arrays.asList(
                    mapper.readValue(imageDTOJson, ImageDTO[].class)
            );

            imageService.uploadProject(files, projectName, imageDTOs);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


}
