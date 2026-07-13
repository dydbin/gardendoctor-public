package com.project.farming.domain.plant.command;

public record PlantAdminCommand(
        String plantName,
        String plantEnglishName,
        String species,
        String season
) {
}
