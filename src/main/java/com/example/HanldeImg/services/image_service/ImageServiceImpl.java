package com.example.HanldeImg.services.image_service;

import com.example.HanldeImg.entity.dto.ImageDTO;
import com.example.HanldeImg.entity.model.Image;
import com.example.HanldeImg.entity.model.Status;
import com.example.HanldeImg.repository.ImageRepository;
import com.example.HanldeImg.scripts.ImageScripts;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {

    private final ImageRepository imageRepository;
    private static final String BASE_UPLOAD_DIRECTORY = "uploads";

    private static final Logger log = LoggerFactory.getLogger(ImageServiceImpl.class);

    @Override
    public void uploadProject(List<MultipartFile> files, String projectName) {

        Path targetDir = Path.of(BASE_UPLOAD_DIRECTORY, projectName);


        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            if (file.isEmpty()) continue;


            try {
                File tempFile = File.createTempFile("upload_", file.getOriginalFilename());
                file.transferTo(tempFile);
                ImageScripts.extractImgWith7z(tempFile.toPath(), targetDir);
                tempFile.delete();

            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("Ошибка при обработке файла " + file.getOriginalFilename(), e);
            }
        }


        try{
            ImageScripts.ensure(
                    System.getenv("GIT_GROUP_NAME"),
                    projectName
            );
            ImageScripts.pushToGitLab(targetDir);
        }catch (Exception e){
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
