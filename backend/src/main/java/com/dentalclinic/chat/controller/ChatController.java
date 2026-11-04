package com.dentalclinic.chat.controller;

import com.dentalclinic.auth.security.CurrentUser;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.chat.dto.ChatMessageResponse;
import com.dentalclinic.chat.dto.ChatSessionResponse;
import com.dentalclinic.chat.dto.SendChatMessageRequest;
import com.dentalclinic.chat.dto.SendChatMessageResponse;
import com.dentalclinic.chat.service.ChatService;
import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Validated
@RestController
@RequestMapping("/api/chat")
@PreAuthorize("hasRole('PATIENT')")
@Tag(name = "Patient Chatbot")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/session")
    public ApiResponse<ChatSessionResponse> getSession(@CurrentUser UserPrincipal currentUser) {
        return ApiResponse.success(chatService.getSession(currentUser.getId()));
    }

    @GetMapping("/messages")
    public ApiResponse<PageResponse<ChatMessageResponse>> getMessages(
            @CurrentUser UserPrincipal currentUser,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page phải lớn hơn hoặc bằng 0")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Size phải lớn hơn hoặc bằng 1")
            @Max(value = 100, message = "Size không được vượt quá 100")
            int size
    ) {
        return ApiResponse.success(chatService.getMessages(currentUser.getId(), page, size));
    }

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SendChatMessageResponse> sendMessage(
            @CurrentUser UserPrincipal currentUser,
            @Valid @RequestBody SendChatMessageRequest request
    ) {
        return ApiResponse.success(
                "Chatbot đã trả lời tin nhắn",
                chatService.sendMessage(currentUser.getId(), request)
        );
    }

    @Operation(description = """
            Trả câu trả lời theo SSE. Thứ tự thành công: `started`, một hoặc nhiều `token`,
            `metadata`, `completed`. Nếu lỗi hoặc quá thời gian chờ, stream kết thúc bằng `error`.
            Câu trả lời ASSISTANT chỉ được lưu khi đã phát hoàn chỉnh và qua kiểm tra an toàn.
            """)
    @PostMapping(
            value = "/messages/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ServerSentEvent<Object>> streamMessage(
            @CurrentUser UserPrincipal currentUser,
            @Valid @RequestBody SendChatMessageRequest request
    ) {
        return chatService.streamMessage(currentUser.getId(), request);
    }

    @DeleteMapping("/messages")
    public ApiResponse<Void> clearHistory(@CurrentUser UserPrincipal currentUser) {
        chatService.clearHistory(currentUser.getId());
        return ApiResponse.success("Đã xóa lịch sử hội thoại", null);
    }
}
