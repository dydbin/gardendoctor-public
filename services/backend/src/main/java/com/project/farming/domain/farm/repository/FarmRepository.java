package com.project.farming.domain.farm.repository;

import com.project.farming.domain.farm.dto.FarmResponse;
import com.project.farming.domain.farm.entity.Farm;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FarmRepository extends JpaRepository<Farm, Long> {
    @Query(value = "SELECT COUNT(*) > 0 FROM farm_info WHERE garden_unique_id = :gardenUniqueId", nativeQuery = true)
    boolean existsAnyByGardenUniqueId(@Param("gardenUniqueId") int gardenUniqueId);

    @Query(value = "SELECT COUNT(*) FROM farm_info", nativeQuery = true)
    long countAllIncludingDeleted();

    @Query("""
        SELECT new com.project.farming.domain.farm.dto.FarmResponse(
            f.farmId,
            f.gardenUniqueId,
            f.operator,
            f.farmName,
            null,
            f.lotNumberAddress,
            null,
            null,
            null,
            null,
            null,
            null,
            f.updatedAt,
            image.imageUrl
        )
        FROM Farm f
        LEFT JOIN ImageFile image ON image.imageFileId = f.farmImageFileId
        ORDER BY f.gardenUniqueId ASC, f.farmId ASC
        """)
    Page<FarmResponse> findAllListResponsesByOrderByGardenUniqueIdAsc(Pageable pageable);

    @Query("""
        SELECT new com.project.farming.domain.farm.dto.FarmResponse(
            f.farmId,
            f.gardenUniqueId,
            f.operator,
            f.farmName,
            null,
            f.lotNumberAddress,
            null,
            null,
            null,
            null,
            null,
            null,
            f.updatedAt,
            image.imageUrl
        )
        FROM Farm f
        LEFT JOIN ImageFile image ON image.imageFileId = f.farmImageFileId
        WHERE f.farmName LIKE :keyword ESCAPE '!'
           OR f.roadNameAddress LIKE :keyword ESCAPE '!'
           OR f.lotNumberAddress LIKE :keyword ESCAPE '!'
        ORDER BY f.gardenUniqueId ASC, f.farmId ASC
        """)
    Page<FarmResponse> findListResponsesByKeywordOrderByGardenUniqueIdAsc(
            @Param("keyword") String keyword, Pageable pageable);

    @Query("""
        SELECT new com.project.farming.domain.farm.dto.FarmResponse(
            f.farmId,
            f.gardenUniqueId,
            f.operator,
            f.farmName,
            null,
            f.lotNumberAddress,
            null,
            null,
            null,
            null,
            null,
            null,
            f.updatedAt,
            image.imageUrl
        )
        FROM Farm f
        LEFT JOIN ImageFile image ON image.imageFileId = f.farmImageFileId
        WHERE f.farmName LIKE :keyword ESCAPE '!'
        ORDER BY f.gardenUniqueId ASC, f.farmId ASC
        """)
    Page<FarmResponse> findAdminListResponsesByFarmNameOrderByGardenUniqueIdAsc(
            @Param("keyword") String keyword, Pageable pageable);

    @Query("""
        SELECT new com.project.farming.domain.farm.dto.FarmResponse(
            f.farmId,
            f.gardenUniqueId,
            f.operator,
            f.farmName,
            null,
            f.lotNumberAddress,
            null,
            null,
            null,
            null,
            null,
            null,
            f.updatedAt,
            image.imageUrl
        )
        FROM Farm f
        LEFT JOIN ImageFile image ON image.imageFileId = f.farmImageFileId
        WHERE f.roadNameAddress LIKE :keyword ESCAPE '!'
           OR f.lotNumberAddress LIKE :keyword ESCAPE '!'
        ORDER BY f.gardenUniqueId ASC, f.farmId ASC
        """)
    Page<FarmResponse> findAdminListResponsesByAddressOrderByGardenUniqueIdAsc(
            @Param("keyword") String keyword, Pageable pageable);

    @Query(value = """
        SELECT
            f.farm_id AS farmId,
            f.garden_unique_id AS gardenUniqueId,
            f.operator AS operator,
            f.farm_name AS farmName,
            f.road_name_address AS roadNameAddress,
            f.lot_number_address AS lotNumberAddress,
            f.facilities AS facilities,
            f.contact AS contact,
            f.latitude AS latitude,
            f.longitude AS longitude,
            f.available AS available,
            f.created_at AS createdAt,
            f.updated_at AS updatedAt,
            image.image_url AS farmImageUrl
        FROM farm_info f
        LEFT JOIN image_files image ON image.image_file_id = f.farm_image_file_id
        WHERE f.deleted = false
          AND f.latitude BETWEEN :minLatitude AND :maxLatitude
          AND f.longitude BETWEEN :minLongitude AND :maxLongitude
          AND ST_Distance_Sphere(
              POINT(:longitude, :latitude),
              POINT(f.longitude, f.latitude)
          ) <= :radiusMeters
        ORDER BY ST_Distance_Sphere(
              POINT(:longitude, :latitude),
              POINT(f.longitude, f.latitude)
          ) ASC,
          f.garden_unique_id ASC,
          f.farm_id ASC
        """, nativeQuery = true)
    Slice<FarmNearbyResponseRow> findNearbyResponseRows(
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("radiusMeters") Double radiusMeters,
            @Param("minLatitude") Double minLatitude,
            @Param("maxLatitude") Double maxLatitude,
            @Param("minLongitude") Double minLongitude,
            @Param("maxLongitude") Double maxLongitude,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT f FROM Farm f WHERE f.gardenUniqueId = :gardenUniqueId")
    Optional<Farm> findReferenceByGardenUniqueIdForShare(@Param("gardenUniqueId") int gardenUniqueId);

    Optional<Farm> findByFarmId(Long farmId);

    @Query("""
        SELECT new com.project.farming.domain.farm.dto.FarmResponse(
            f.farmId,
            f.gardenUniqueId,
            f.operator,
            f.farmName,
            f.roadNameAddress,
            f.lotNumberAddress,
            f.facilities,
            f.contact,
            f.latitude,
            f.longitude,
            f.available,
            f.createdAt,
            f.updatedAt,
            image.imageUrl
        )
        FROM Farm f
        LEFT JOIN ImageFile image ON image.imageFileId = f.farmImageFileId
        WHERE f.farmId = :farmId
        """)
    Optional<FarmResponse> findDetailResponseByFarmId(@Param("farmId") Long farmId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT f FROM Farm f WHERE f.farmName = :farmName ORDER BY f.farmId ASC")
    List<Farm> findOtherFarmCandidatesForShare(@Param("farmName") String farmName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Farm f WHERE f.farmId = :farmId")
    Optional<Farm> findByFarmIdForUpdate(@Param("farmId") Long farmId);
}
