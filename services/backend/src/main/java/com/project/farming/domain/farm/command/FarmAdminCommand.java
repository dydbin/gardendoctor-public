package com.project.farming.domain.farm.command;

public record FarmAdminCommand(
        Integer gardenUniqueId,
        String operator,
        String farmName,
        String roadNameAddress,
        String lotNumberAddress,
        String facilities,
        String contact,
        Double latitude,
        Double longitude,
        Boolean available
) {
}
