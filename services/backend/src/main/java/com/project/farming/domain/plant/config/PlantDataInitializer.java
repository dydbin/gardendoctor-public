package com.project.farming.domain.plant.config;

import com.project.farming.domain.plant.repository.PlantRepository;
import com.project.farming.domain.plant.service.PlantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * 식물 정보 총 13개
 * - AI로 질병 진단이 가능한 주요 시설 원예 작물 정보 저장
 * - 초기 데이터이므로 추후 수정 가능
 * - 작물 12종
 * - 사용자 입력 옵션 1개
 */

@Order(3)
@Slf4j
@RequiredArgsConstructor
@Component
@ConditionalOnProperty(value = "app.init.seed-data.enabled", havingValue = "true", matchIfMissing = true)
public class PlantDataInitializer implements CommandLineRunner {

    private final PlantRepository plantRepository;
    private final PlantService plantService;

    @Override
    public void run(String... args) throws Exception {
        if (plantRepository.countAllIncludingDeleted() > 0) {
            log.info("plant_info 테이블에 초기 식물 데이터가 이미 존재합니다.");
            return;
        }
        initializePlants();
    }

    private void initializePlants() throws IOException {
        InputStream inputStream = getClass().getResourceAsStream("/data/plantList.xlsx");
        if (inputStream == null) {
            throw new IllegalArgumentException("plantList 엑셀 파일을 찾을 수 없습니다.");
        }

        int savedCount = 0;
        try (inputStream; Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) {
                    continue;
                }
                plantService.saveSeedPlant(
                        getCellValue(row.getCell(0)),
                        getCellValue(row.getCell(1)),
                        getCellValue(row.getCell(2)),
                        getCellValue(row.getCell(3)),
                        getCellValue(row.getCell(4)),
                        getCellValue(row.getCell(5))
                );
                savedCount++;
            }
        }
        log.info("plant_info 테이블에 {}개의 초기 식물 데이터가 저장되었습니다.", savedCount);
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue().trim();
        return "";
    }
}
