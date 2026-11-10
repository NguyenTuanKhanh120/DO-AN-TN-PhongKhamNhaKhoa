package com.dentalclinic.chat;

import com.dentalclinic.auth.security.AccessTokenClaims;
import com.dentalclinic.auth.security.CustomUserDetailsService;
import com.dentalclinic.auth.security.JwtAuthenticationFilter;
import com.dentalclinic.auth.security.JwtTokenProvider;
import com.dentalclinic.auth.security.RestSecurityExceptionHandler;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.auth.service.AuthSessionService;
import com.dentalclinic.ai.conversation.model.ChatSessionPreference;
import com.dentalclinic.ai.conversation.model.ChatIntent;
import com.dentalclinic.ai.slot.model.ChatSlotSuggestion;
import com.dentalclinic.chat.controller.ChatController;
import com.dentalclinic.chat.dto.ChatMessageResponse;
import com.dentalclinic.chat.dto.ChatSessionResponse;
import com.dentalclinic.chat.dto.ChatStreamTokenEvent;
import com.dentalclinic.chat.dto.SendChatMessageRequest;
import com.dentalclinic.chat.dto.SendChatMessageResponse;
import com.dentalclinic.chat.entity.ChatSender;
import com.dentalclinic.chat.service.ChatService;
import com.dentalclinic.common.exception.GlobalExceptionHandler;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.config.SecurityConfig;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestSecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class ChatControllerSecurityTest {

    private static final String TEST_JWT_SECRET = generateSecret();
    private static final Instant NOW = Instant.parse("2026-10-29T03:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private AuthSessionService authSessionService;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "chat-controller-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/chat/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void shouldRejectNonPatientRole() throws Exception {
        mockMvc.perform(get("/api/chat/session").with(user("doctor@example.com").roles("DOCTOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldReturnCurrentPatientSessionWithoutClientIdentityParameters() throws Exception {
        UserPrincipal principal = patientPrincipal();
        ChatSessionResponse response = new ChatSessionResponse(UUID.randomUUID(), null, NOW, NOW);
        when(chatService.getSession(principal.getId())).thenReturn(response);

        mockMvc.perform(get("/api/chat/session").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(response.id().toString()))
                .andExpect(jsonPath("$.data.userId").doesNotExist());

        verify(chatService).getSession(principal.getId());
    }

    @Test
    void shouldReturnPaginatedMessages() throws Exception {
        UserPrincipal principal = patientPrincipal();
        ChatMessageResponse message = new ChatMessageResponse(
                UUID.randomUUID(),
                ChatSender.ASSISTANT,
                "Xin chào",
                JsonNodeFactory.instance.objectNode(),
                NOW
        );
        when(chatService.getMessages(principal.getId(), 0, 20)).thenReturn(new PageResponse<>(
                List.of(message), 0, 20, 1, 1, true, true, false, false
        ));

        mockMvc.perform(get("/api/chat/messages").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].sender").value("ASSISTANT"))
                .andExpect(jsonPath("$.data.content[0].content").value("Xin chào"));
    }

    @Test
    void shouldValidateMessageLengthAndContent() throws Exception {
        mockMvc.perform(post("/api/chat/messages")
                        .with(user(patientPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldAllowPatientToSendMessage() throws Exception {
        UserPrincipal principal = patientPrincipal();
        SendChatMessageRequest request = new SendChatMessageRequest("Tôi bị đau răng");
        ChatSlotSuggestion appointmentCase = new ChatSlotSuggestion(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 11, 10),
                ChatSessionPreference.MORNING,
                2,
                LocalTime.of(8, 0),
                LocalTime.of(11, 0),
                LocalTime.of(9, 30),
                OffsetDateTime.parse("2026-11-10T09:30:00+07:00")
        );
        SendChatMessageResponse response = new SendChatMessageResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Bạn nên đến nha sĩ để được kiểm tra.",
                List.of(ChatIntent.SYMPTOM_CONSULTATION),
                List.of(),
                List.of(),
                List.of(),
                List.of(appointmentCase),
                false,
                NOW
        );
        when(chatService.sendMessage(principal.getId(), request)).thenReturn(response);

        mockMvc.perform(post("/api/chat/messages")
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Tôi bị đau răng\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.messageId").value(response.messageId().toString()))
                .andExpect(jsonPath("$.data.answer").value(response.answer()))
                .andExpect(jsonPath("$.data.intents[0]").value("SYMPTOM_CONSULTATION"))
                .andExpect(jsonPath("$.data.suggestedServices").isArray())
                .andExpect(jsonPath("$.data.suggestedDoctors").isArray())
                .andExpect(jsonPath("$.data.suggestedSlots").isArray())
                .andExpect(jsonPath("$.data.suggestedSlots[0].session").value("MORNING"))
                .andExpect(jsonPath("$.data.suggestedSlots[0].queueNumber").value(2))
                .andExpect(jsonPath("$.data.suggestedSlots[0].sessionStartTime").value("08:00:00"))
                .andExpect(jsonPath("$.data.suggestedSlots[0].sessionEndTime").value("11:00:00"))
                .andExpect(jsonPath("$.data.suggestedSlots[0].estimatedStartTime").value("09:30:00"))
                .andExpect(jsonPath("$.data.suggestedSlots[0].estimatedStartAt")
                        .value("2026-11-10T09:30:00+07:00"))
                .andExpect(jsonPath("$.data.suggestedSlots[0].startAt").doesNotExist())
                .andExpect(jsonPath("$.data.suggestedSlots[0].endAt").doesNotExist())
                .andExpect(jsonPath("$.data.needsClarification").value(false));

        verify(chatService).sendMessage(principal.getId(), request);
    }

    @Test
    void shouldRequirePatientRoleForStreamingMessages() throws Exception {
        String body = "{\"message\":\"Tôi bị đau răng\"}";

        mockMvc.perform(post("/api/chat/messages/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/chat/messages/stream")
                        .with(user("doctor@example.com").roles("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldStreamSseForCurrentPatient() throws Exception {
        UserPrincipal principal = patientPrincipal();
        SendChatMessageRequest requestBody = new SendChatMessageRequest("Tôi bị đau răng");
        when(chatService.streamMessage(principal.getId(), requestBody)).thenReturn(Flux.just(
                ServerSentEvent.builder((Object) new ChatStreamTokenEvent("Xin chào"))
                        .event("token")
                        .build()
        ));

        MvcResult asyncResult = mockMvc.perform(post("/api/chat/messages/stream")
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"message\":\"Tôi bị đau răng\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:token")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "data:{\"content\":"
                )));

        verify(chatService).streamMessage(principal.getId(), requestBody);
    }

    @Test
    void shouldKeepJwtAuthenticatedStreamAuthorizedDuringAsyncDispatch() throws Exception {
        UserPrincipal principal = patientPrincipal();
        UUID sessionId = UUID.randomUUID();
        AccessTokenClaims claims = new AccessTokenClaims(principal.getId(), sessionId, Role.PATIENT);
        SendChatMessageRequest requestBody = new SendChatMessageRequest("Tôi bị đau răng");

        when(jwtTokenProvider.extractAccessTokenClaims("patient-access-token")).thenReturn(claims);
        when(authSessionService.authenticateAccessToken(claims)).thenReturn(principal);
        when(chatService.streamMessage(principal.getId(), requestBody)).thenReturn(Flux.just(
                ServerSentEvent.builder((Object) new ChatStreamTokenEvent("Xin chào"))
                        .event("token")
                        .build()
        ));

        MvcResult asyncResult = mockMvc.perform(post("/api/chat/messages/stream")
                        .header("Authorization", "Bearer patient-access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"message\":\"Tôi bị đau răng\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:token")));

        verify(authSessionService).authenticateAccessToken(claims);
        verify(chatService).streamMessage(principal.getId(), requestBody);
    }

    @Test
    void shouldClearOnlyCurrentPatientHistory() throws Exception {
        UserPrincipal principal = patientPrincipal();

        mockMvc.perform(delete("/api/chat/messages").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã xóa lịch sử hội thoại"));

        verify(chatService).clearHistory(principal.getId());
    }

    private UserPrincipal patientPrincipal() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("patient@example.com");
        user.setPasswordHash("bcrypt-hash");
        user.setFullName("Bệnh nhân kiểm thử");
        user.setRole(Role.PATIENT);
        user.setStatus(UserStatus.ACTIVE);
        return UserPrincipal.from(user);
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }
}
