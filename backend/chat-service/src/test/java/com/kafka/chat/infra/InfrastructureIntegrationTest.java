package com.kafka.chat.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka.chat.client.UserDirectoryClient;
import com.kafka.chat.client.UserView;
import com.kafka.chat.dto.ChatDtos.ChatMessageResponse;
import com.kafka.chat.dto.ChatDtos.CreateDirectRoomRequest;
import com.kafka.chat.dto.ChatDtos.EditMessageRequest;
import com.kafka.chat.dto.ChatDtos.InviteRoomParticipantsRequest;
import com.kafka.chat.dto.ChatDtos.MessageReactionRequest;
import com.kafka.chat.dto.ChatDtos.RoomPreferenceRequest;
import com.kafka.chat.dto.ChatDtos.RoomReadSummaryResponse;
import com.kafka.chat.dto.ChatDtos.SendMessageRequest;
import com.kafka.chat.dto.ChatMessageEvent;
import com.kafka.chat.model.ChatMessageDocument;
import com.kafka.chat.model.ChatRoom;
import com.kafka.chat.notification.NotificationDtos.NotificationListResponse;
import com.kafka.chat.notification.NotificationService;
import com.kafka.chat.notification.UserNotificationRepository;
import com.kafka.chat.outbox.ChatMessageOutboxService;
import com.kafka.chat.outbox.OutboxEvent;
import com.kafka.chat.outbox.OutboxEventRepository;
import com.kafka.chat.outbox.OutboxEventStatus;
import com.kafka.chat.outbox.OutboxRelayService;
import com.kafka.chat.repository.ChatMessageRepository;
import com.kafka.chat.repository.ChatRoomRepository;
import com.kafka.chat.search.ChatMessageSearchDocument;
import com.kafka.chat.search.ChatMessageSearchRepository;
import com.kafka.chat.security.AuthUser;
import com.kafka.chat.service.ChatReadReceiptService;
import com.kafka.chat.service.ChatService;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end infrastructure test for chat-service across its own Postgres, MongoDB,
 * Elasticsearch, Redis and Kafka. User data is owned by auth-service, so the
 * UserDirectoryClient boundary is mocked with an in-memory directory instead of a
 * real users table.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class InfrastructureIntegrationTest {
    private static final Path TEST_UPLOAD_ROOT = Path.of("build", "test-uploads", "testcontainers").toAbsolutePath();

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("kafka_chat_test")
            .withUsername("kafka")
            .withPassword("kafka");

    @Container
    static final MongoDBContainer mongodb = new MongoDBContainer(DockerImageName.parse("mongo:7"))
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofMinutes(4));

    @Container
    static final ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.15.5")
    )
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
            .withStartupTimeout(Duration.ofMinutes(4));

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Container
    static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1")
    )
            .withStartupTimeout(Duration.ofMinutes(3));

    @MockBean
    private UserDirectoryClient userDirectory;

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

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final Map<String, UserView> directory = new ConcurrentHashMap<>();

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
        registry.add("app.storage.type", () -> "local");
        registry.add("app.storage.local.root-path", TEST_UPLOAD_ROOT::toString);
        registry.add("app.outbox.relay.initial-delay-ms", () -> "600000");
        registry.add("app.outbox.relay.fixed-delay-ms", () -> "600000");
        registry.add("app.push.fcm.enabled", () -> "false");
        registry.add("management.tracing.sampling.probability", () -> "0.0");
    }

    @BeforeEach
    void setUpDirectory() {
        directory.clear();
        when(userDirectory.findByEmail(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(directory.get(normalize(invocation.getArgument(0)))));
        when(userDirectory.findByEmails(any())).thenAnswer(invocation -> {
            Collection<String> emails = invocation.getArgument(0);
            return emails.stream().map(email -> directory.get(normalize(email))).filter(Objects::nonNull).toList();
        });
        when(userDirectory.search(any(), anyInt())).thenAnswer(invocation -> new ArrayList<>(directory.values()));
        when(userDirectory.findAll()).thenAnswer(invocation -> new ArrayList<>(directory.values()));
    }

    private AuthUser register(long id, String email, String name) {
        directory.put(normalize(email), new UserView(id, email, name, "LOCAL", "", null));
        return new AuthUser(id, email, name, "USER");
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    @Test
    void infrastructureContainersSupportCoreReadWritePaths() throws Exception {
        AuthUser savedUser = register(1L, "testcontainers@example.com", "테스트컨테이너");

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

        List<String> readyIds = outboxRelayService.findReadyEventIds();
        assertThat(readyIds).contains(pendingEvent.getId());
        int published = 0;
        for (String readyId : readyIds) {
            if (outboxRelayService.publishOne(readyId)) {
                published++;
            }
        }
        assertThat(published).isGreaterThanOrEqualTo(1);
        OutboxEvent publishedEvent = outboxEventRepository.findById(pendingEvent.getId()).orElseThrow();
        assertThat(publishedEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);

        AuthUser recipient = register(2L, "recipient@example.com", "수신자");
        ChatRoom notificationRoom = new ChatRoom(
                "알림 테스트 방",
                "알림 저장 검증",
                savedUser.getEmail()
        );
        notificationRoom.addParticipant(recipient.getEmail());
        notificationRoom = chatRoomRepository.save(notificationRoom);
        String notificationRoomId = notificationRoom.getId();
        assertThat(chatService.roomParticipants(notificationRoom.getId(), savedUser))
                .extracting(participant -> participant.email())
                .contains(savedUser.getEmail(), recipient.getEmail());
        AuthUser directRecipient = register(3L, "direct-recipient@example.com", "직접대화수신자");
        String directRoomId = chatService.findOrCreateDirectRoom(new CreateDirectRoomRequest(directRecipient.getEmail()), savedUser).id();
        transactionTemplate.executeWithoutResult(status -> {
            ChatRoom hiddenDirectRoom = chatRoomRepository.findById(directRoomId).orElseThrow();
            hiddenDirectRoom.hideFor(savedUser.getEmail());
            chatRoomRepository.save(hiddenDirectRoom);
        });
        assertThat(chatService.rooms("", savedUser))
                .extracting(room -> room.id())
                .doesNotContain(directRoomId);

        var reopenedDirectRoom = chatService.findOrCreateDirectRoom(new CreateDirectRoomRequest(directRecipient.getEmail()), savedUser);
        assertThat(reopenedDirectRoom.id()).isEqualTo(directRoomId);
        assertThat(reopenedDirectRoom.name()).isEqualTo(directRecipient.getName());
        assertThat(chatService.findOrCreateDirectRoom(new CreateDirectRoomRequest(savedUser.getEmail()), directRecipient).name())
                .isEqualTo(savedUser.getName());
        assertThat(chatService.rooms("", savedUser))
                .extracting(room -> room.id())
                .contains(directRoomId);
        ChatRoom directRoom = chatRoomRepository.findById(directRoomId).orElseThrow();
        ChatMessageEvent directNotificationEvent = new ChatMessageEvent(
                "direct-notification-" + UUID.randomUUID(),
                directRoom.getId(),
                directRoom.getName(),
                savedUser.getEmail(),
                savedUser.getName(),
                "1:1 알림 제목 테스트",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.now()
        );
        notificationService.createChatMessageNotifications(directNotificationEvent, Set.of(directRecipient.getEmail()));
        assertThat(notificationService.notifications(directRecipient).notifications())
                .singleElement()
                .satisfies(notification -> {
                    assertThat(notification.title()).isEqualTo(savedUser.getName());
                    assertThat(notification.body()).isEqualTo("1:1 알림 제목 테스트");
                });

        AuthUser invitedUser = register(4L, "invited@example.com", "초대유저");
        assertThat(chatService.inviteRoomParticipants(
                notificationRoom.getId(),
                new InviteRoomParticipantsRequest(List.of(invitedUser.getEmail())),
                savedUser
        ))
                .extracting(participant -> participant.email())
                .contains(invitedUser.getEmail());
        chatService.leaveRoom(notificationRoom.getId(), invitedUser);
        assertThat(chatService.roomParticipants(notificationRoom.getId(), savedUser))
                .extracting(participant -> participant.email())
                .doesNotContain(invitedUser.getEmail());
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
                    assertThat(notification.targetRoomId()).isEqualTo(notificationRoomId);
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
        assertThat(chatService.markMessageDelivered(notificationRoom.getId(), mutedNotificationEvent.messageId(), recipient))
                .satisfies(delivery -> {
                    assertThat(delivery.messageId()).isEqualTo(mutedNotificationEvent.messageId());
                    assertThat(delivery.deliveredCount()).isEqualTo(1);
                    assertThat(delivery.readCount()).isZero();
                    assertThat(delivery.deliveryStatus()).isEqualTo("DELIVERED");
                });

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
        assertThat(chatService.messages(notificationRoom.getId(), savedUser))
                .filteredOn(message -> message.id().equals(mutedNotificationEvent.messageId()))
                .singleElement()
                .satisfies(message -> {
                    assertThat(message.readCount()).isEqualTo(1);
                    assertThat(message.deliveredCount()).isEqualTo(1);
                    assertThat(message.deliveryStatus()).isEqualTo("READ");
                });

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
