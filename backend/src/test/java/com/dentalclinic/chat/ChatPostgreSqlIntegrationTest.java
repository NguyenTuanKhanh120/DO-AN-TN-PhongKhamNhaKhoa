package com.dentalclinic.chat;

import com.dentalclinic.ai.conversation.mapper.ChatSemanticContextMapper;
import com.dentalclinic.ai.conversation.model.ChatDateRange;
import com.dentalclinic.ai.conversation.model.ChatEntities;
import com.dentalclinic.ai.conversation.model.ChatEntityReference;
import com.dentalclinic.ai.conversation.model.ChatFollowUpType;
import com.dentalclinic.ai.conversation.model.ChatIntent;
import com.dentalclinic.ai.conversation.model.ChatSemanticContext;
import com.dentalclinic.ai.conversation.model.ChatSessionPreference;
import com.dentalclinic.ai.conversation.model.ChatUnderstanding;
import com.dentalclinic.ai.conversation.service.ChatContextResolver;
import com.dentalclinic.chat.entity.ChatSender;
import com.dentalclinic.chat.repository.ChatMessageRepository;
import com.dentalclinic.chat.repository.ChatSessionRepository;
import com.dentalclinic.chat.service.ChatPersistenceService;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import com.dentalclinic.user.repository.UserRepository;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.crypto.SecretKey;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("development")
class ChatPostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();

    @Autowired
    private ChatPersistenceService chatPersistenceService;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ChatContextResolver contextResolver;

    @Autowired
    private ChatSemanticContextMapper contextMapper;

    private UUID createdUserId;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "chat-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 15);
        registry.add("app.ai.enabled", () -> false);
    }

    @AfterEach
    void cleanUp() {
        if (createdUserId != null) {
            userRepository.deleteById(createdUserId);
            createdUserId = null;
        }
    }

    @Test
    void shouldKeepOneSessionPersistMessagesAndClearHistory() {
        User patient = createPatient();

        var firstSession = chatPersistenceService.getSession(patient.getId());
        var secondSession = chatPersistenceService.getSession(patient.getId());
        assertThat(secondSession.id()).isEqualTo(firstSession.id());
        assertThat(chatSessionRepository.count()).isGreaterThanOrEqualTo(1);

        chatPersistenceService.appendMessage(patient.getId(), ChatSender.USER, "Tôi bị đau răng");
        chatPersistenceService.appendMessage(patient.getId(), ChatSender.ASSISTANT, "Bạn nên đi khám trực tiếp");
        var messages = chatPersistenceService.getMessages(patient.getId(), 0, 20);
        assertThat(messages.totalElements()).isEqualTo(2);
        assertThat(messages.content())
                .extracting(message -> message.content())
                .containsExactlyInAnyOrder("Tôi bị đau răng", "Bạn nên đi khám trực tiếp");

        var session = chatSessionRepository.findByUserId(patient.getId()).orElseThrow();
        session.setContextData(JsonNodeFactory.instance.objectNode().put("symptom", "đau răng"));
        chatSessionRepository.saveAndFlush(session);

        chatPersistenceService.clearHistory(patient.getId());

        assertThat(chatMessageRepository.findAllBySessionId(
                session.getId(),
                org.springframework.data.domain.Pageable.unpaged()
        ).getTotalElements()).isZero();
        var retainedSession = chatSessionRepository.findByUserId(patient.getId()).orElseThrow();
        assertThat(retainedSession.getContextData().isEmpty()).isTrue();
    }

    @Test
    void shouldPersistStructuredAssistantMetadataAndSemanticContext() {
        User patient = createPatient();
        var started = chatPersistenceService.startTurn(patient.getId(), "Chiều mai còn lịch không?");
        var metadata = JsonNodeFactory.instance.objectNode()
                .put("needsClarification", true);
        metadata.putArray("intents").add("AVAILABLE_SLOTS");
        var context = JsonNodeFactory.instance.objectNode()
                .put("sessionPreference", "AFTERNOON");

        var completed = chatPersistenceService.completeTurn(
                patient.getId(),
                "Bạn vui lòng cho biết bác sĩ hoặc dịch vụ.",
                metadata,
                context
        );

        assertThat(started.userMessage().sender()).isEqualTo(ChatSender.USER);
        assertThat(completed.sender()).isEqualTo(ChatSender.ASSISTANT);
        assertThat(completed.metadata().path("needsClarification").asBoolean()).isTrue();
        var session = chatSessionRepository.findByUserId(patient.getId()).orElseThrow();
        assertThat(session.getContextData().path("sessionPreference").asText())
                .isEqualTo("AFTERNOON");
    }

    @Test
    void shouldReplaceOldSemanticContextForIndependentQuestionWithoutDeletingHistory() {
        User patient = createPatient();
        persistPreviousConversation(patient.getId());
        var previous = contextMapper.fromJson(chatPersistenceService.readContext(patient.getId()));
        var resolved = contextResolver.resolve(previous, new ChatUnderstanding(
                List.of(ChatIntent.SYMPTOM_CONSULTATION),
                new ChatEntities(List.of("ê buốt răng"), null, null, null, null, null, null, null, null, null),
                ChatFollowUpType.NONE
        ));
        chatPersistenceService.startTurn(patient.getId(), "Tôi bị ê buốt răng");
        chatPersistenceService.completeTurn(patient.getId(), "Thông tin về ê buốt răng",
                JsonNodeFactory.instance.objectNode(), contextMapper.toJson(resolved.context()));

        var stored = contextMapper.fromJson(chatPersistenceService.readContext(patient.getId()));
        assertThat(stored).isEqualTo(new ChatSemanticContext(
                List.of(ChatIntent.SYMPTOM_CONSULTATION), List.of("ê buốt răng"), null, null, null, null, null
        ));
        var messages = chatPersistenceService.getMessages(patient.getId(), 0, 20);
        assertThat(messages.totalElements()).isEqualTo(4);
        assertThat(messages.content()).extracting(message -> message.content())
                .contains("Bác sĩ Demo sáng mai còn lịch không?", "Tôi bị ê buốt răng");
    }

    @Test
    void shouldPersistRefinedDateAndSessionWhileKeepingDoctorForExplicitFollowUp() {
        User patient = createPatient();
        persistPreviousConversation(patient.getId());
        var previous = contextMapper.fromJson(chatPersistenceService.readContext(patient.getId()));
        var nextDate = LocalDate.of(2026, 11, 21);
        var resolved = contextResolver.resolve(previous, new ChatUnderstanding(
                List.of(ChatIntent.OUT_OF_SCOPE),
                new ChatEntities(List.of(), null, null, null, null, nextDate, null, null,
                        ChatSessionPreference.AFTERNOON, null),
                ChatFollowUpType.TEMPORAL_REFINEMENT
        ));
        chatPersistenceService.startTurn(patient.getId(), "Chiều ngày 21/11 thì sao?");
        chatPersistenceService.completeTurn(patient.getId(), "Thông tin lịch buổi chiều",
                JsonNodeFactory.instance.objectNode(), contextMapper.toJson(resolved.context()));

        var stored = contextMapper.fromJson(chatPersistenceService.readContext(patient.getId()));
        assertThat(stored.selectedDoctor()).isEqualTo(previous.selectedDoctor());
        assertThat(stored.dateRange()).isEqualTo(ChatDateRange.single(nextDate));
        assertThat(stored.sessionPreference()).isEqualTo(ChatSessionPreference.AFTERNOON);
        assertThat(chatPersistenceService.getMessages(patient.getId(), 0, 20).totalElements()).isEqualTo(4);
    }

    private void persistPreviousConversation(UUID userId) {
        var previous = new ChatSemanticContext(
                List.of(ChatIntent.DOCTOR_AVAILABLE_SLOTS), List.of("đau răng"),
                new ChatEntityReference(UUID.randomUUID(), "Bác sĩ Demo"),
                new ChatEntityReference(UUID.randomUUID(), "Cạo vôi răng"),
                ChatDateRange.single(LocalDate.of(2026, 11, 20)), ChatSessionPreference.MORNING, "08:00"
        );
        chatPersistenceService.startTurn(userId, "Bác sĩ Demo sáng mai còn lịch không?");
        chatPersistenceService.completeTurn(userId, "Thông tin lịch buổi sáng",
                JsonNodeFactory.instance.objectNode(), contextMapper.toJson(previous));
    }

    @Test
    void shouldEnforceUniqueSessionPerUserAtDatabaseLevel() {
        User patient = createPatient();
        chatPersistenceService.getSession(patient.getId());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into chat_sessions (user_id) values (?)",
                patient.getId()
        )).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_chat_sessions_user");

        Boolean migrationSucceeded = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where version = '12'",
                Boolean.class
        );
        assertThat(migrationSucceeded).isTrue();
    }

    private User createPatient() {
        User user = new User();
        user.setEmail("ai02-patient-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$ai02.integration.test.hash");
        user.setFullName("Bệnh nhân AI-02");
        user.setRole(Role.PATIENT);
        user.setStatus(UserStatus.ACTIVE);
        User saved = userRepository.saveAndFlush(user);
        createdUserId = saved.getId();
        return saved;
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }
}
