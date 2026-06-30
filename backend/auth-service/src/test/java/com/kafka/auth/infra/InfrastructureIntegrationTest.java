package com.kafka.auth.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka.auth.chat.dto.ChatDtos.ChatMessageResponse;
import com.kafka.auth.chat.dto.ChatDtos.EditMessageRequest;
import com.kafka.auth.chat.dto.ChatDtos.MessageReactionRequest;
import com.kafka.auth.chat.dto.ChatDtos.RoomPreferenceRequest;
import com.kafka.auth.chat.dto.ChatDtos.SendMessageRequest;
import com.kafka.auth.chat.dto.ChatMessageEvent;
import com.kafka.auth.chat.dto.ChatDtos.RoomReadSummaryResponse;
import com.kafka.auth.chat.model.ChatMessageDocument;
import com.kafka.auth.chat.model.ChatRoom;
import com.kafka.auth.chat.repository.ChatMessageRepository;
import com.kafka.auth.chat.repository.ChatRoomRepository;
import com.kafka.auth.chat.search.ChatMessageSearchDocument;
import com.kafka.auth.chat.search.ChatMessageSearchRepository;
import com.kafka.auth.chat.service.ChatReadReceiptService;
import com.kafka.auth.chat.service.ChatService;
import com.kafka.auth.model.AuthProvider;
import com.kafka.auth.model.UserAccount;
import com.kafka.auth.notification.NotificationDtos.NotificationListResponse;
import com.kafka.auth.notification.NotificationService;
import com.kafka.auth.notification.UserNotificationRepository;
import com.kafka.auth.outbox.ChatMessageOutboxService;
import com.kafka.auth.outbox.OutboxEvent;
import com.kafka.auth.outbox.OutboxEventRepository;
import com.kafka.auth.outbox.OutboxEventStatus;
import com.kafka.auth.outbox.OutboxRelayService;
import com.kafka.auth.repository.UserAccountRepository;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class InfrastructureIntegrationTest {
    private static final Path TEST_UPLOAD_ROOT = Path.of("build", "test-uploads", "testcontainers").toAbsolutePath();

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("kafka_project_test")
            .withUsername("kafka")
            .withPassword("kafka");

    @Container
    static final MongoDBContainer mongodb = new MongoDBContainer(DockerImageName.parse("mongo:7"));

    @Container
    static final ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.15.5")
    )
            .withEnv("xpack.security.enabled", "false")
            .withStartupTimeout(Duration.ofMinutes(2));

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Container
    static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1")
    );

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatMessageSearchRepository chatMessageSearchRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private KafkaTemplate<String, ChatMessageEvent> kafkaTemplate;

    @Autowired
    private ChatMessageOutboxService chatMessageOutboxService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxRelayService outboxRelayService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserNotificationRepository userNotificationRepository;

    @Autowired
    private ChatReadReceiptService chatReadReceiptService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.data.mongodb.uri", mongodb::getReplicaSetUrl);
        registry.add("spring.elasticsearch.uris", () -> "http://" + elasticsearch.getHttpHostAddress());
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.consumer.group-id", () -> "chat-service-test-" + UUID.randomUUID());
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("app.dev.seed-users", () -> "false");
        registry.add("app.storage.type", () -> "local");
        registry.add("app.storage.local.root-path", TEST_UPLOAD_ROOT::toString);
        registry.add("app.outbox.relay.initial-delay-ms", () -> "600000");
        registry.add("app.outbox.relay.fixed-delay-ms", () -> "600000");
        registry.add("app.push.fcm.enabled", () -> "false");
        registry.add("management.tracing.sampling.probability", () -> "0.0");
    }

    @Test
    void infrastructureContainersSupportCoreReadWritePaths() throws Exception {
        UserAccount savedUser = userAccountRepository.save(new UserAccount(
                "testcontainers@example.com",
                "테스트컨테이너",
                "encoded-password",
                AuthProvider.LOCAL
        ));
        assertThat(userAccountRepository.findByEmail(savedUser.getEmail())).isPresent();

        ChatMessageDocument mongoMessage = chatMessageRepository.save(new ChatMessageDocument(
                "mongo-" + UUID.randomUUID(),
                "room-testcontainers",
                "테스트 방",
                savedUser.getEmail(),
                savedUser.getName(),
                "몽고 저장 테스트 메시지",
                null,
                null,
                null,
                null,
                Instant.now()
        ));
        assertThat(chatMessageRepository.findById(mongoMessage.getId())).isPresent();

        ChatMessageSearchDocument searchDocument = chatMessageSearchRepository.save(new ChatMessageSearchDocument(
                "search-" + UUID.randomUUID(),
                "room-testcontainers",
                "검색 테스트 방",
                savedUser.getEmail(),
                savedUser.getName(),
                "엘라스틱 자동완성 테스트 메시지",
                Instant.now()
        ));
        assertThat(chatMessageSearchRepository.findById(searchDocument.getId())).isPresent();

        redisTemplate.opsForValue().set("testcontainers:health", "ok");
        assertThat(redisTemplate.opsForValue().get("testcontainers:health")).isEqualTo("ok");

        ChatMessageEvent event = new ChatMessageEvent(
                "kafka-" + UUID.randomUUID(),
                "room-testcontainers",
                "카프카 테스트 방",
                savedUser.getEmail(),
                savedUser.getName(),
                "카프카 전송 테스트 메시지",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.now()
        );
        kafkaTemplate.send("chat-messages", event.roomId(), event).get();

        ChatMessageEvent outboxEvent = new ChatMessageEvent(
                "outbox-" + UUID.randomUUID(),
                "room-testcontainers",
                "카프카 테스트 방",
                savedUser.getEmail(),
                savedUser.getName(),
                "outbox 발행 테스트 메시지",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.now()
        );
        chatMessageOutboxService.append(outboxEvent, "chat-messages");
        OutboxEvent pendingEvent = outboxEventRepository.findAll()
                .stream()
                .filter(outbox -> outboxEvent.messageId().equals(outbox.getAggregateId()))
                .findFirst()
                .orElseThrow();
        assertThat(pendingEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);

        assertThat(outboxRelayService.publishReadyEvents()).isGreaterThanOrEqualTo(1);
        OutboxEvent publishedEvent = outboxEventRepository.findById(pendingEvent.getId()).orElseThrow();
        assertThat(publishedEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);

        UserAccount recipient = userAccountRepository.save(new UserAccount(
                "recipient@example.com",
                "수신자",
                "encoded-password",
                AuthProvider.LOCAL
        ));
        ChatRoom notificationRoom = chatRoomRepository.save(new ChatRoom(
                "알림 테스트 방",
                "알림 저장 검증",
                savedUser.getEmail()
        ));
        ChatMessageEvent notificationEvent = new ChatMessageEvent(
                "notification-" + UUID.randomUUID(),
                notificationRoom.getId(),
                notificationRoom.getName(),
                savedUser.getEmail(),
                savedUser.getName(),
                "알림 저장 테스트 메시지",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.now()
        );

        notificationService.createChatMessageNotifications(notificationEvent, Set.of(recipient.getEmail()));
        NotificationListResponse notificationList = notificationService.notifications(recipient);
        assertThat(notificationList.unreadCount()).isEqualTo(1);
        assertThat(notificationList.notifications())
                .singleElement()
                .satisfies(notification -> {
                    assertThat(notification.targetRoomId()).isEqualTo(notificationRoom.getId());
                    assertThat(notification.body()).isEqualTo("알림 저장 테스트 메시지");
                });

        notificationService.markAllRead(recipient);
        assertThat(userNotificationRepository.countByRecipientEmailAndReadAtIsNull(recipient.getEmail())).isZero();

        assertThat(chatService.updateRoomPreference(notificationRoom.getId(), new RoomPreferenceRequest(true, true), recipient))
                .satisfies(preference -> {
                    assertThat(preference.pinned()).isTrue();
                    assertThat(preference.muted()).isTrue();
                });
        ChatMessageEvent mutedNotificationEvent = new ChatMessageEvent(
                "muted-notification-" + UUID.randomUUID(),
                notificationRoom.getId(),
                notificationRoom.getName(),
                savedUser.getEmail(),
                savedUser.getName(),
                "알림 끄기 테스트 메시지",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.now()
        );
        chatService.persistAndBroadcast(mutedNotificationEvent);
        assertThat(userNotificationRepository.countByRecipientEmailAndReadAtIsNull(recipient.getEmail())).isZero();

        String unreadKey = "state:unread:" + recipient.getEmail().toLowerCase() + ":" + notificationRoom.getId();
        redisTemplate.opsForValue().set(unreadKey, "3");
        RoomReadSummaryResponse readSummary = chatReadReceiptService.markRead(notificationRoom, recipient, Instant.now());
        assertThat(readSummary.currentUserLastReadAt()).isNotNull();
        assertThat(readSummary.receipts())
                .anySatisfy(receipt -> {
                    assertThat(receipt.email()).isEqualTo(recipient.getEmail());
                    assertThat(receipt.lastReadAt()).isNotNull();
                });
        assertThat(redisTemplate.opsForValue().get(unreadKey)).isNull();

        ChatMessageDocument reactionMessage = chatMessageRepository.save(new ChatMessageDocument(
                "reaction-" + UUID.randomUUID(),
                notificationRoom.getId(),
                notificationRoom.getName(),
                savedUser.getEmail(),
                savedUser.getName(),
                "반응 저장 테스트 메시지",
                null,
                null,
                null,
                null,
                Instant.now()
        ));
        ChatMessageResponse reactionAdded = chatService.toggleReaction(
                notificationRoom.getId(),
                reactionMessage.getId(),
                new MessageReactionRequest("👍"),
                recipient
        );
        assertThat(reactionAdded.reactions())
                .singleElement()
                .satisfies(reaction -> {
                    assertThat(reaction.emoji()).isEqualTo("👍");
                    assertThat(reaction.count()).isEqualTo(1);
                    assertThat(reaction.reactedByMe()).isTrue();
                    assertThat(reaction.reactorEmails()).containsExactly(recipient.getEmail());
                });

        ChatMessageResponse reactionRemoved = chatService.toggleReaction(
                notificationRoom.getId(),
                reactionMessage.getId(),
                new MessageReactionRequest("👍"),
                recipient
        );
        assertThat(reactionRemoved.reactions()).isEmpty();

        ChatMessageResponse editedMessage = chatService.editMessage(
                notificationRoom.getId(),
                reactionMessage.getId(),
                new EditMessageRequest("수정된 반응 테스트 메시지"),
                savedUser
        );
        assertThat(editedMessage.content()).isEqualTo("수정된 반응 테스트 메시지");
        assertThat(editedMessage.editedAt()).isNotNull();
        assertThat(chatMessageRepository.findById(reactionMessage.getId()))
                .get()
                .satisfies(message -> {
                    assertThat(message.getContent()).isEqualTo("수정된 반응 테스트 메시지");
                    assertThat(message.getEditedAt()).isNotNull();
                });
        assertThat(chatMessageSearchRepository.findById(reactionMessage.getId()))
                .get()
                .extracting(ChatMessageSearchDocument::getContent)
                .isEqualTo("수정된 반응 테스트 메시지");

        ChatMessageEvent replyEvent = chatService.publishMessage(
                notificationRoom.getId(),
                new SendMessageRequest("답장 저장 테스트", null, reactionMessage.getId()),
                recipient
        );
        OutboxEvent replyOutbox = outboxEventRepository.findAll()
                .stream()
                .filter(outbox -> replyEvent.messageId().equals(outbox.getAggregateId()))
                .findFirst()
                .orElseThrow();
        ChatMessageEvent replyPayload = objectMapper.readValue(replyOutbox.getPayload(), ChatMessageEvent.class);
        assertThat(replyPayload.replyToMessageId()).isEqualTo(reactionMessage.getId());
        assertThat(replyPayload.replyToSenderName()).isEqualTo(savedUser.getName());
        assertThat(replyPayload.replyToContent()).isEqualTo("수정된 반응 테스트 메시지");
    }
}
