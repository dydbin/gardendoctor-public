package com.project.farming.domain.farm.config;

import com.project.farming.domain.farm.entity.Farm;
import com.project.farming.domain.farm.repository.FarmRepository;
import com.project.farming.domain.farm.service.FarmService;
import com.project.farming.global.exception.ImageFileNotFoundException;
import com.project.farming.global.image.entity.DefaultImages;
import com.project.farming.global.image.entity.ImageFile;
import com.project.farming.global.image.repository.ImageFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 공개 저장소에는 연락처가 포함된 원본 공공데이터 export를 포함하지 않습니다.
 * 이 initializer는 기능 재현을 위한 소규모 합성 fixture만 읽습니다.
 */

@Order(2)
@Slf4j
@RequiredArgsConstructor
@Component
@ConditionalOnProperty(value = "app.init.seed-data.enabled", havingValue = "true", matchIfMissing = true)
public class FarmDataInitializer implements CommandLineRunner {

    private static final String PUBLIC_FARM_SEED = "/data/farms-public.tsv";
    private static final List<String> REQUIRED_COLUMNS = List.of(
            "gardenUniqueId",
            "operator",
            "farmName",
            "roadNameAddress",
            "lotNumberAddress",
            "facilities",
            "contact",
            "latitude",
            "longitude",
            "createdAt",
            "updatedAt"
    );
    private final FarmRepository farmRepository;
    private final FarmService farmService;
    private final ImageFileRepository imageFileRepository;

    private static final String DEFAULT_STRING = "N/A";
    private static final double DEFAULT_LAT_LNG = 0.0;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public void run(String... args) throws Exception {
        if (farmRepository.countAllIncludingDeleted() > 0) {
            log.info("farm_info 테이블에 초기 텃밭 데이터가 이미 존재합니다.");
            return;
        }
        initializeFarms();
    }

    private void initializeFarms() throws IOException {
        farmService.saveOtherFarmOption();
        List<Farm> farmList = loadFarmData(PUBLIC_FARM_SEED);
        farmService.saveFarms(farmList);
        log.info("farm_info 테이블에 {}개의 초기 텃밭 데이터가 저장되었습니다.",  farmList.size() + 1);
    }

    private List<Farm> loadFarmData(String fileName) throws IOException {
        InputStream inputStream = getClass().getResourceAsStream(fileName);
        if (inputStream == null) {
            throw new IllegalArgumentException(fileName + " 공개 fixture를 찾을 수 없습니다.");
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new IllegalArgumentException(fileName + " 공개 fixture의 header가 비어 있습니다.");
            }

            Map<String, Integer> columnIndexMap = getColumnIndexMap(headerLine);
            ImageFile defaultImageFile = imageFileRepository.findByS3Key(DefaultImages.DEFAULT_FARM_IMAGE)
                    .orElseThrow(() -> new ImageFileNotFoundException("기본 텃밭 이미지가 존재하지 않습니다."));
            List<Farm> farmList = new ArrayList<>();
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank() || line.stripLeading().startsWith("#")) {
                    continue;
                }
                List<String> values = Arrays.asList(line.split("\\t", -1));
                try {
                    farmList.add(createFarmFromValues(values, columnIndexMap, defaultImageFile));
                } catch (RuntimeException exception) {
                    throw new IllegalArgumentException(
                            fileName + " 공개 fixture의 " + lineNumber + "번째 줄이 올바르지 않습니다.",
                            exception
                    );
                }
            }
            return farmList;
        }
    }

    private Map<String, Integer> getColumnIndexMap(String headerLine) {
        Map<String, Integer> columnIndexMap = new HashMap<>();
        String[] columns = headerLine.split("\\t", -1);
        for (int index = 0; index < columns.length; index++) {
            columnIndexMap.put(columns[index].trim(), index);
        }
        List<String> missingColumns = REQUIRED_COLUMNS.stream()
                .filter(column -> !columnIndexMap.containsKey(column))
                .toList();
        if (!missingColumns.isEmpty()) {
            throw new IllegalArgumentException("공개 fixture에 필수 column이 없습니다: " + missingColumns);
        }
        return columnIndexMap;
    }

    private Farm createFarmFromValues(
            List<String> values,
            Map<String, Integer> columnIndexMap,
            ImageFile defaultImageFile) {
        int gardenUniqueId = Integer.parseInt(value(values, columnIndexMap, "gardenUniqueId"));
        String operator = getOrDefault(values, columnIndexMap, "operator", DEFAULT_STRING);
        String farmName = getOrDefault(values, columnIndexMap, "farmName", DEFAULT_STRING);
        String roadNameAddress = getOrDefault(values, columnIndexMap, "roadNameAddress", DEFAULT_STRING);
        String lotNumberAddress = getOrDefault(values, columnIndexMap, "lotNumberAddress", DEFAULT_STRING);
        String facilities = getOrDefault(values, columnIndexMap, "facilities", DEFAULT_STRING);
        String contact = getOrDefault(values, columnIndexMap, "contact", DEFAULT_STRING);
        Double latitude = parseDoubleOrDefault(values, columnIndexMap, "latitude", DEFAULT_LAT_LNG);
        Double longitude = parseDoubleOrDefault(values, columnIndexMap, "longitude", DEFAULT_LAT_LNG);
        LocalDate createdAt = parseDate(values, columnIndexMap, "createdAt");
        LocalDate updatedAt = parseDate(values, columnIndexMap, "updatedAt");

        return Farm.builder()
                .gardenUniqueId(gardenUniqueId)
                .operator(operator)
                .farmName(farmName)
                .roadNameAddress(roadNameAddress)
                .lotNumberAddress(lotNumberAddress)
                .facilities(facilities)
                .contact(contact)
                .latitude(latitude)
                .longitude(longitude)
                .available(true)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .farmImageFileId(defaultImageFile.getImageFileId())
                .build();
    }

    private double parseDoubleOrDefault(
            List<String> values,
            Map<String, Integer> columnIndexMap,
            String column,
            double defaultValue) {
        String val = value(values, columnIndexMap, column);
        return val.isBlank() ? defaultValue : Double.parseDouble(val);
    }

    private LocalDate parseDate(
            List<String> values,
            Map<String, Integer> columnIndexMap,
            String column) {
        String val = value(values, columnIndexMap, column);
        if (val.isBlank()) return LocalDate.now();
        return LocalDate.parse(val, DATE_FORMATTER);
    }

    private String getOrDefault(
            List<String> values,
            Map<String, Integer> columnIndexMap,
            String column,
            String defaultVal) {
        String val = value(values, columnIndexMap, column);
        return val.isBlank() || val.equals("-") ? defaultVal : val;
    }

    private String value(
            List<String> values,
            Map<String, Integer> columnIndexMap,
            String column) {
        int index = columnIndexMap.get(column);
        if (index >= values.size()) {
            throw new IllegalArgumentException("column 값이 없습니다: " + column);
        }
        return values.get(index).trim();
    }
}
