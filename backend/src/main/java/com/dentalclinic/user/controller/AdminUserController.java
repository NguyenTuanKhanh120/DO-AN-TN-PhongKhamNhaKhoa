package com.dentalclinic.user.controller;

import com.dentalclinic.auth.security.CurrentUser;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.user.dto.AdminUserResponse;
import com.dentalclinic.user.dto.UpdateUserStatusRequest;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.UserStatus;
import com.dentalclinic.user.service.AdminUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminUserResponse>> getUsers(
            @RequestParam(required = false)
            @Size(max = 255, message = "Từ khóa tìm kiếm không được vượt quá 255 ký tự")
            String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page phải lớn hơn hoặc bằng 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Size phải lớn hơn hoặc bằng 1")
            @Max(value = 100, message = "Size không được vượt quá 100")
            int size
    ) {
        return ApiResponse.success(adminUserService.getUsers(search, role, status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminUserResponse> getUser(@PathVariable UUID id) {
        return ApiResponse.success(adminUserService.getUser(id));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<AdminUserResponse> updateStatus(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        AdminUserResponse response = adminUserService.updateStatus(
                currentUser.getId(),
                id,
                request.status()
        );
        return ApiResponse.success("Cập nhật trạng thái tài khoản thành công", response);
    }
}
