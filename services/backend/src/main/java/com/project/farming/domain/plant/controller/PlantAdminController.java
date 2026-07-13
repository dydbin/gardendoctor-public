package com.project.farming.domain.plant.controller;

import com.project.farming.domain.plant.command.PlantAdminCommand;
import com.project.farming.domain.plant.dto.PlantAdminRequest;
import com.project.farming.domain.plant.dto.PlantResponse;
import com.project.farming.domain.plant.service.PlantAdminService;
import com.project.farming.domain.plant.service.PlantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Plant Admin API", description = "AI 기능을 사용할 수 있는 식물 관련 **관리자 전용** API")
@RequestMapping("/admin/plants")
@RequiredArgsConstructor
@Controller
public class PlantAdminController {

    private final PlantAdminService plantAdminService;
    private final PlantService plantService;

    @GetMapping("/create")
    @Operation(summary = "새로운 식물 정보 등록 페이지 (관리자 전용)",
            description = "새로운 식물 정보를 등록하는 페이지 - **관리자만 접근 가능합니다.**")
    public String showCreatePlantPage() {
        return "plant/create-plant";
    }

    @PostMapping("/createProc")
    @Operation(summary = "새로운 식물 정보 등록 (관리자 전용)",
            description = """
                    AI 기능을 활용할 수 있는 새로운 식물 정보를 등록합니다.
                    **관리자만 접근 가능합니다.**
                    식물 정보는 DTO로 전달하며, 이미지 파일은 선택적으로 함께 첨부할 수 있습니다.
                    enctype은 multipart/form-data입니다.
                    """)
    public String createPlant(
            @Parameter(description = "식물 정보")
            @Valid @ModelAttribute PlantAdminRequest request,
            @Parameter(description = "업로드할 식물 이미지 파일")
            @RequestParam("imageFile") MultipartFile imageFile) {
        plantAdminService.savePlant(toCommand(request), imageFile);
        return "redirect:/admin/plants";
    }

    @GetMapping
    @Operation(summary = "전체 식물 목록 조회 페이지 (관리자 전용)",
            description = "DB에 등록된 모든 식물을 ID 순으로 조회합니다. **관리자만 접근 가능합니다.**")
    public String showPlantListPage(
            @PageableDefault(size = 20) Pageable pageable,
            Model model) {
        Page<PlantResponse> plantPage = plantAdminService.findAllPlants(pageable);
        addPageModel(model, plantPage, "");
        return "plant/plant-list";
    }

    @GetMapping("/search")
    @Operation(summary = "식물 목록 검색 (관리자 전용)",
            description = """
                    한글 또는 영어 식물 이름이 입력 키워드로 시작하는 식물을 ID 순으로 페이지 조회합니다.
                    **관리자만 접근 가능합니다.**
                    """)
    public String showSearchPlantListPage(
            @RequestParam String keyword,
            @PageableDefault(size = 20) Pageable pageable,
            Model model) {
        Page<PlantResponse> plantPage = plantAdminService.findPlantsByKeyword(keyword, pageable);
        addPageModel(model, plantPage, keyword);
        return "plant/plant-list";
    }

    @GetMapping("/{plantId}")
    @Operation(summary = "특정 식물 정보 조회 페이지 (관리자 전용)",
            description = "식물 ID에 해당하는 식물의 상세 정보를 조회합니다. **관리자만 접근 가능합니다.**")
    public String showPlantPage(@PathVariable Long plantId, Model model) {
        PlantResponse plant = plantService.findPlant(plantId);
        model.addAttribute("plant", plant);
        return "plant/plant-detail";
    }

    @GetMapping("/update")
    @Operation(summary = "특정 식물 정보 수정 페이지 (관리자 전용)",
            description = "식물 ID에 해당하는 식물의 정보를 수정하는 페이지 - **관리자만 접근 가능합니다.**")
    public String showUpdatePlantPage(@RequestParam Long plantId, Model model) {
        PlantResponse plant = plantService.findPlant(plantId);
        model.addAttribute("plant", plant);
        return "plant/update-plant";
    }

    @PostMapping("/update/{plantId}")
    @Operation(summary = "특정 식물 정보 수정 (관리자 전용)",
            description = """
                    식물 ID에 해당하는 식물의 정보를 수정합니다. **관리자만 접근 가능합니다.**
                    식물 정보는 DTO로 전달하며, 이미지 파일은 선택적으로 함께 첨부할 수 있습니다.
                    enctype은 multipart/form-data입니다.
                    """)
    public String updatePlant(@PathVariable Long plantId,
            @Parameter(description = "식물 정보")
            @Valid @ModelAttribute PlantAdminRequest request,
            @Parameter(description = "업로드할 식물 이미지 파일")
            @RequestPart("imageFile") MultipartFile imageFile) {
        plantAdminService.updatePlant(plantId, toCommand(request), imageFile);
        return "redirect:/admin/plants/" + plantId;
    }

    @GetMapping("/delete")
    @Operation(summary = "특정 식물 정보 삭제 페이지 (관리자 전용)",
            description = "식물 ID에 해당하는 식물의 정보를 삭제하는 페이지 - **관리자만 접근 가능합니다.**")
    public String showDeletePlantPage() {
        return "plant/delete-plant";
    }

    @PostMapping("/delete/{plantId}")
    @Operation(summary = "특정 식물 정보 삭제 (관리자 전용)",
            description = "식물 ID에 해당하는 식물의 정보를 삭제합니다. **관리자만 접근 가능합니다.**")
    public String deletePlant(@PathVariable Long plantId) {
        plantAdminService.deletePlant(plantId);
        return "redirect:/admin/plants";
    }

    private PlantAdminCommand toCommand(PlantAdminRequest request) {
        return new PlantAdminCommand(
                request.getPlantName(),
                request.getPlantEnglishName(),
                request.getSpecies(),
                request.getSeason()
        );
    }

    private void addPageModel(Model model, Page<PlantResponse> plantPage, String keyword) {
        model.addAttribute("plantPage", plantPage);
        model.addAttribute("plantList", plantPage.getContent());
        model.addAttribute("keyword", keyword);
        model.addAttribute("isSearch", !keyword.isBlank());
    }
}
