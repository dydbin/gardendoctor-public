package com.project.farming.domain.user.controller;

import com.project.farming.domain.user.command.UserAdminCommand;
import com.project.farming.domain.user.dto.UserAdminRequest;
import com.project.farming.domain.user.dto.UserAdminResponse;
import com.project.farming.domain.user.service.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "User Admin API", description = "사용자 관련 **관리자 전용** API")
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@Controller
public class UserAdminController {

    private final UserAdminService userAdminService;

    @GetMapping
    @Operation(summary = "전체 사용자 목록 조회 페이지 (관리자 전용)",
            description = "DB에 등록된 모든 사용자를 ID 순으로 조회합니다. **관리자만 접근 가능합니다.**")
    public String showUserListPage(
            @PageableDefault(size = 20) Pageable pageable,
            Model model) {
        Page<UserAdminResponse> userPage = userAdminService.findAllUsers(pageable);
        addPageModel(model, userPage, "name", "");
        return "user/user-list";
    }

    @GetMapping("/search")
    @Operation(summary = "사용자 목록 검색 (관리자 전용)",
            description = """
                    searchType 기본값은 name입니다.
                    1. 별명(name)이 입력 키워드로 시작하는 사용자를 별명 순으로 페이지 조회합니다.
                    2. 이메일(email)이 입력 키워드로 시작하는 사용자를 이메일 순으로 페이지 조회합니다.
                    **관리자만 접근 가능합니다.**
                    """)
    public String showSearchUserListPage(
            @Parameter(description = "검색 조건: 사용자 별명(name) 또는 이메일(email)")
            @RequestParam(defaultValue = "name") String searchType,
            @RequestParam String keyword,
            @PageableDefault(size = 20) Pageable pageable,
            Model model) {
        Page<UserAdminResponse> userPage = userAdminService.findUsersByKeyword(
                searchType, keyword, pageable);
        addPageModel(model, userPage, searchType, keyword);
        return "user/user-list";
    }

    @GetMapping("/{userId}")
    @Operation(summary = "특정 사용자 조회 페이지 (관리자 전용)",
            description = "사용자 ID에 해당하는 사용자의 상세 정보를 조회합니다. **관리자만 접근 가능합니다.**")
    public String showUserPage(@PathVariable Long userId, Model model) {
        UserAdminResponse user = userAdminService.findUser(userId);
        model.addAttribute("user", user);
        return "user/user-detail";
    }

    @GetMapping("/update")
    @Operation(summary = "특정 사용자 정보 수정 페이지 (관리자 전용)",
            description = "사용자 ID에 해당하는 사용자의 정보를 수정하는 페이지 - **관리자만 접근 가능합니다.**")
    public String showUpdateUserPage(@RequestParam Long userId, Model model) {
        UserAdminResponse user = userAdminService.findUser(userId);
        model.addAttribute("user", user);
        return "user/update-user";
    }

    @PostMapping("/update/{userId}")
    @Operation(summary = "특정 사용자 정보 수정 (관리자 전용)",
            description = """
                    사용자 ID에 해당하는 사용자의 정보를 수정합니다. **관리자만 접근 가능합니다.**
                    사용자 정보는 DTO로 전달하며, 이미지 파일은 선택적으로 함께 첨부할 수 있습니다.
                    enctype은 multipart/form-data입니다.
                    """)
    public String updateUser(@PathVariable Long userId,
                             @Parameter(description = "사용자 정보") @Valid @ModelAttribute UserAdminRequest request,
                             @Parameter(description = "업로드할 프로필 이미지 파일")
                             @RequestParam("imageFile") MultipartFile imageFile) {
        userAdminService.updateUser(userId, toCommand(request), imageFile);
        return "redirect:/admin/users/" + userId;
    }

    @GetMapping("/delete")
    @Operation(summary = "특정 사용자 삭제 페이지 (관리자 전용)",
            description = "사용자 ID에 해당하는 사용자를 삭제하는 페이지 - **관리자만 접근 가능합니다.**")
    public String showDeleteUserPage() {
        return "user/delete-user";
    }

    @PostMapping("/delete/{userId}")
    @Operation(summary = "특정 사용자 정보 삭제 (관리자 전용)",
            description = "사용자 ID에 해당하는 사용자를 삭제합니다. **관리자만 접근 가능합니다.**")
    public String deleteUser(@PathVariable Long userId) {
        userAdminService.deleteUser(userId);
        return "redirect:/admin/users";
    }

    private void addPageModel(
            Model model,
            Page<UserAdminResponse> userPage,
            String searchType,
            String keyword) {
        model.addAttribute("userPage", userPage);
        model.addAttribute("userList", userPage.getContent());
        model.addAttribute("searchType", searchType);
        model.addAttribute("keyword", keyword);
        model.addAttribute("isSearch", !keyword.isBlank());
    }

    private UserAdminCommand toCommand(UserAdminRequest request) {
        return new UserAdminCommand(
                request.getEmail(),
                request.getNickname(),
                request.getOauthProvider(),
                request.getOauthId(),
                request.getRole(),
                request.getFcmToken(),
                request.getSubscriptionStatus()
        );
    }
}
