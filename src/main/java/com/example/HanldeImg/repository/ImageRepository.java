package com.example.HanldeImg.repository;

import com.example.HanldeImg.entity.model.Image;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRepository extends JpaRepository<Image, Long> {
}
