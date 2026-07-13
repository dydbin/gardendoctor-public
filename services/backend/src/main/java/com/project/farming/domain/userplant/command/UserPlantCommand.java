package com.project.farming.domain.userplant.command;

import com.project.farming.domain.userplant.entity.UserPlant;
import java.time.LocalDateTime;
import java.util.Objects;

public record UserPlantCommand(
        String plantName,
        String plantNickname,
        int gardenUniqueId,
        String plantingPlace,
        LocalDateTime plantedDate,
        String notes,
        boolean notificationEnabled,
        int waterIntervalDays,
        int pruneIntervalDays,
        int fertilizeIntervalDays,
        boolean watered,
        boolean pruned,
        boolean fertilized
) {
    public UserPlantCommand {
        Objects.requireNonNull(plantName, "plantName must not be null");
        Objects.requireNonNull(plantNickname, "plantNickname must not be null");
        UserPlant.validateCareIntervals(
                waterIntervalDays, pruneIntervalDays, fertilizeIntervalDays);
    }
}
