package com.project.farming.domain.farm.repository;

import java.time.LocalDate;

public interface FarmNearbyResponseRow {
    Long getFarmId();

    Integer getGardenUniqueId();

    String getOperator();

    String getFarmName();

    String getRoadNameAddress();

    String getLotNumberAddress();

    String getFacilities();

    String getContact();

    Double getLatitude();

    Double getLongitude();

    Boolean getAvailable();

    LocalDate getCreatedAt();

    LocalDate getUpdatedAt();

    String getFarmImageUrl();
}
