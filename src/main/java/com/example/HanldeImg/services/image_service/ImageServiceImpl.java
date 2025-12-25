package com.example.HanldeImg.services.image_service;

import com.example.HanldeImg.scripts.ApkScripts;
import com.example.HanldeImg.scripts.ImageScripts;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {

    private static final String BASE_UPLOAD_DIRECTORY = "uploads";

    private static final Logger log = LoggerFactory.getLogger(ImageServiceImpl.class);

    private static String baseName(String filename) {
        if (filename == null) return "unknown";
        String name = filename;
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(0, dot) : name;
    }

    @Override
    public void uploadProject(List<MultipartFile> files, String projectName) {
        Path targetDir = Path.of(BASE_UPLOAD_DIRECTORY, projectName);

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;

            try {
                File tempFile = File.createTempFile("upload_", "_" + file.getOriginalFilename());
                file.transferTo(tempFile);

                String folderName = baseName(file.getOriginalFilename()); // system / vendor
                Path imageOutDir = targetDir.resolve(folderName);

                ImageScripts.extractImgWith7z(tempFile.toPath(), imageOutDir);
                tempFile.delete();

            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("Ошибка при обработке файла " + file.getOriginalFilename(), e);
            }
        }

        ApkScripts.decodeApksToProjectRoot(targetDir);

        try {
            ImageScripts.ensure(System.getenv("GIT_GROUP_NAME"), projectName);
            ImageScripts.pushToGitLab(targetDir);
        } catch (Exception e) {
            log.info("Ошибка при загрузки образа на gitlab");
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public void updateProject(List<MultipartFile> files, String dirName) {

        Path targetDir = Path.of(BASE_UPLOAD_DIRECTORY, dirName);

        List<Path> tempPaths = new ArrayList<>();

        try {
            // 1) Сохраняем MultipartFile во временные файлы -> List<Path>
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) continue;

                File tempFile = File.createTempFile("upload_", "_" + file.getOriginalFilename());
                file.transferTo(tempFile);
                tempPaths.add(tempFile.toPath());
            }

            // 2) ensure проекта в GitLab
            ImageScripts.ensure(System.getenv("GIT_GROUP_NAME"), dirName);

            // 3) обновление: очистка папки + распаковка + commit + push
            ImageScripts.updateImages(targetDir, tempPaths);

        } catch (Exception e) {
            log.info("Ошибка при обновлении проекта на gitlab");
            throw new RuntimeException(e.getMessage(), e);

        } finally {
            for (Path p : tempPaths) {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ex) {
                    log.warn("Не смог удалить temp файл: {}", p, ex);
                }
            }
        }
    }


}
