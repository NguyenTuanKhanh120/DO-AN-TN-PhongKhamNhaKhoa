package com.dentalclinic.ai.knowledge.controller;

import com.dentalclinic.ai.knowledge.dto.KnowledgeSearchTestRequest;
import com.dentalclinic.ai.knowledge.dto.KnowledgeSearchTestResponse;
import com.dentalclinic.ai.knowledge.dto.KnowledgeStatusResponse;
import com.dentalclinic.ai.knowledge.dto.KnowledgeSyncResponse;
import com.dentalclinic.ai.knowledge.service.AdminKnowledgeService;
import com.dentalclinic.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai/knowledge")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin AI Knowledge")
public class AdminKnowledgeController {

    private final AdminKnowledgeService adminKnowledgeService;

    public AdminKnowledgeController(AdminKnowledgeService adminKnowledgeService) {
        this.adminKnowledgeService = adminKnowledgeService;
    }

    @GetMapping("/status")
    public ApiResponse<KnowledgeStatusResponse> getStatus() {
        return ApiResponse.success(adminKnowledgeService.getStatus());
    }

    @PostMapping("/sync")
    public ApiResponse<KnowledgeSyncResponse> synchronize() {
        return ApiResponse.success(
                "Đã xử lý yêu cầu đồng bộ kho tri thức",
                adminKnowledgeService.synchronize()
        );
    }

    @PostMapping("/search-test")
    public ApiResponse<KnowledgeSearchTestResponse> searchTest(
            @Valid @RequestBody KnowledgeSearchTestRequest request
    ) {
        return ApiResponse.success(
                "Kiểm thử truy xuất kho tri thức thành công",
                adminKnowledgeService.searchTest(request)
        );
    }
}
