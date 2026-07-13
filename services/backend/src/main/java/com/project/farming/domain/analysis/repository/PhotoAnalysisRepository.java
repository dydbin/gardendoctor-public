package com.project.farming.domain.analysis.repository;

import com.project.farming.domain.analysis.entity.PhotoAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhotoAnalysisRepository extends JpaRepository<PhotoAnalysis, Long> {
}
