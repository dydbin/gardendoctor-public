package com.project.farming.domain.userplant.repository;

public record UserPlantCareTaskRow(
        Long userPlantId,
        Long userId,
        String plantName,
        String plantNickname,
        boolean wateringDue,
        boolean pruningDue,
        boolean fertilizingDue
) {
}
