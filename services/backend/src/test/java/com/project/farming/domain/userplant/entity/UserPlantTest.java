package com.project.farming.domain.userplant.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserPlantTest {

    @Test
    void explicitDailyStatusCorrectionShouldReplaceFlagsWithoutErasingCompletionDates() {
        LocalDate today = LocalDate.now();
        UserPlant userPlant = UserPlant.builder()
                .waterIntervalDays(7)
                .pruneIntervalDays(14)
                .fertilizeIntervalDays(30)
                .build();

        userPlant.replaceDailyCareStatus(true, false, false);
        userPlant.replaceDailyCareStatus(false, true, false);

        assertThat(userPlant.isWatered()).isFalse();
        assertThat(userPlant.isPruned()).isTrue();
        assertThat(userPlant.isFertilized()).isFalse();
        assertThat(userPlant.getLastWateredDate()).isEqualTo(today);
        assertThat(userPlant.getLastPrunedDate()).isEqualTo(today);
    }

    @Test
    void careIntervalUpdateShouldRejectValuesOutsideOneToThreeHundredSixtyFiveDays() {
        UserPlant userPlant = UserPlant.builder()
                .waterIntervalDays(1)
                .pruneIntervalDays(1)
                .fertilizeIntervalDays(1)
                .build();

        assertThatThrownBy(() -> userPlant.updateUserPlantIntervalDays(0, 10, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> userPlant.updateUserPlantIntervalDays(10, 366, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void careDueDatesShouldFollowLastCompletionDateAndIntervals() {
        LocalDate today = LocalDate.now();
        UserPlant userPlant = UserPlant.builder()
                .waterIntervalDays(7)
                .pruneIntervalDays(14)
                .fertilizeIntervalDays(30)
                .build();

        userPlant.replaceDailyCareStatus(true, true, true);

        assertThat(userPlant.getLastWateredDate()).isEqualTo(today);
        assertThat(userPlant.getNextWateringDate()).isEqualTo(today.plusDays(7));
        assertThat(userPlant.getNextPruningDate()).isEqualTo(today.plusDays(14));
        assertThat(userPlant.getNextFertilizingDate()).isEqualTo(today.plusDays(30));

        userPlant.updateUserPlantIntervalDays(3, 4, 5);

        assertThat(userPlant.getNextWateringDate()).isEqualTo(today.plusDays(3));
        assertThat(userPlant.getNextPruningDate()).isEqualTo(today.plusDays(4));
        assertThat(userPlant.getNextFertilizingDate()).isEqualTo(today.plusDays(5));

        userPlant.replaceDailyCareStatus(false, false, false);

        assertThat(userPlant.getLastWateredDate()).isEqualTo(today);
        assertThat(userPlant.getNextWateringDate()).isEqualTo(today.plusDays(3));
        assertThat(userPlant.getNextPruningDate()).isEqualTo(today.plusDays(4));
        assertThat(userPlant.getNextFertilizingDate()).isEqualTo(today.plusDays(5));
    }

    @Test
    void careDueDatesShouldRemainEmptyUntilACompletionExists() {
        UserPlant userPlant = UserPlant.builder()
                .waterIntervalDays(7)
                .pruneIntervalDays(14)
                .fertilizeIntervalDays(30)
                .build();

        userPlant.replaceDailyCareStatus(false, false, false);

        assertThat(userPlant.getLastWateredDate()).isNull();
        assertThat(userPlant.getNextWateringDate()).isNull();
        assertThat(userPlant.getNextPruningDate()).isNull();
        assertThat(userPlant.getNextFertilizingDate()).isNull();
    }
}
