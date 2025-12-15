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
    @Transactional
    public void uploadProject(List<MultipartFile> files, String projectName, List<ImageDTO> imageDTOList) {

        Path targetDir = Path.of(BASE_UPLOAD_DIRECTORY, projectName);

        List<Image> imagesToSave = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            if (file.isEmpty()) continue;

            ImageDTO dto = imageDTOList.get(i);

            try {
                File tempFile = File.createTempFile("upload_", file.getOriginalFilename());
                file.transferTo(tempFile);

                ImageScripts.extractImgWith7z(tempFile.toPath(), targetDir);

                // Создаём сущность для БД ТОЛЬКО если распаковка успешна
                Image image = new Image();
                image.setImgName(dto.name() != null ? dto.name() : file.getOriginalFilename());
                image.setCreateTime(
                        dto.createdDate() != null
                                ? dto.createdDate().atStartOfDay()
                                : LocalDateTime.now()
                );
                image.setStatus(
                        dto.status() != null
                                ? Status.valueOf(dto.status())
                                : Status.ACTIVE
                );

                imagesToSave.add(image);

                tempFile.delete();

            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("Ошибка при обработке файла " + file.getOriginalFilename(), e);
            }
        }

        imageRepository.saveAll(imagesToSave);

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
    public void updateProject(List<MultipartFile> files, String dirName) {/*TODO*/}

}
