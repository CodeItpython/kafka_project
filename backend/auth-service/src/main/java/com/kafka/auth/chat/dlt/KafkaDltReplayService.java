package com.kafka.auth.chat.dlt;

import com.kafka.auth.chat.dlt.KafkaDltDtos.DltMessageListResponse;
import com.kafka.auth.chat.dlt.KafkaDltDtos.DltMessageResponse;
import com.kafka.auth.chat.dlt.KafkaDltDtos.DltReplayRequest;
import com.kafka.auth.chat.dlt.KafkaDltDtos.DltReplayResponse;
import com.kafka.auth.chat.dto.ChatMessageEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class KafkaDltReplayService {
    private static final Duration TOPIC_LOOKUP_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);

    private final KafkaTemplate<String, ChatMessageEvent> kafkaTemplate;
    private final String bootstrapServers;
    private final String chatTopic;
    private final String dltTopic;

    public KafkaDltReplayService(
            KafkaTemplate<String, ChatMessageEvent> kafkaTemplate,
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${app.chat.topic}") String chatTopic,
            @Value("${app.chat.dlt.topic:}") String configuredDltTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.bootstrapServers = bootstrapServers;
        this.chatTopic = chatTopic;
        this.dltTopic = StringUtils.hasText(configuredDltTopic) ? configuredDltTopic : chatTopic + "-dlt";
    }

    public DltMessageListResponse messages(int limit) {
        int normalizedLimit = normalizeLimit(limit);
        List<DltRecord> records = readDltRecords(Set.of(), normalizedLimit);
        return new DltMessageListResponse(
                dltTopic,
                normalizedLimit,
                records.stream().map(DltRecord::response).toList()
        );
    }

    public DltReplayResponse replay(DltReplayRequest request) {
        int limit = normalizeLimit(request.normalizedLimit());
        Set<String> requestedMessageIds = normalizeMessageIds(request.messageIds());
        List<DltRecord> records = readDltRecords(requestedMessageIds, limit);
        List<DltMessageResponse> replayedMessages = new ArrayList<>();

        if (!request.dryRun()) {
            for (DltRecord record : records) {
                try {
                    kafkaTemplate.send(chatTopic, record.event().messageId(), record.event()).get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                    replayedMessages.add(record.response());
                } catch (Exception exception) {
                    throw new IllegalStateException("DLT 메시지 재발행에 실패했습니다. messageId=" + record.event().messageId(), exception);
                }
            }
        } else {
            replayedMessages.addAll(records.stream().map(DltRecord::response).toList());
        }

        log.info("Kafka DLT replay requested. sourceTopic={}, targetTopic={}, dryRun={}, scannedCount={}, replayedCount={}",
                dltTopic,
                chatTopic,
                request.dryRun(),
                records.size(),
                replayedMessages.size());

        return new DltReplayResponse(
                dltTopic,
                chatTopic,
                request.dryRun(),
                records.size(),
                replayedMessages.size(),
                replayedMessages
        );
    }

    private List<DltRecord> readDltRecords(Set<String> messageIds, int limit) {
        try (KafkaConsumer<String, ChatMessageEvent> consumer = new KafkaConsumer<>(consumerProperties())) {
            List<PartitionInfo> partitions = consumer.partitionsFor(dltTopic, TOPIC_LOOKUP_TIMEOUT);
            if (partitions == null || partitions.isEmpty()) {
                return List.of();
            }
            List<TopicPartition> topicPartitions = partitions.stream()
                    .map(partition -> new TopicPartition(partition.topic(), partition.partition()))
                    .toList();
            consumer.assign(topicPartitions);
            consumer.seekToBeginning(topicPartitions);
            java.util.Map<TopicPartition, Long> endOffsets = consumer.endOffsets(topicPartitions);

            List<DltRecord> records = new ArrayList<>();
            while (records.size() < limit && hasRemainingRecords(consumer, endOffsets)) {
                ConsumerRecords<String, ChatMessageEvent> polledRecords = consumer.poll(POLL_TIMEOUT);
                if (polledRecords.isEmpty()) {
                    break;
                }
                for (ConsumerRecord<String, ChatMessageEvent> record : polledRecords) {
                    if (record.value() == null) {
                        continue;
                    }
                    if (!messageIds.isEmpty() && !messageIds.contains(record.value().messageId())) {
                        continue;
                    }
                    records.add(new DltRecord(toResponse(record), record.value()));
                    if (records.size() >= limit) {
                        break;
                    }
                }
            }
            return records;
        } catch (TimeoutException exception) {
            throw new IllegalStateException("Kafka DLT 토픽을 조회하지 못했습니다. topic=" + dltTopic, exception);
        }
    }

    private boolean hasRemainingRecords(KafkaConsumer<String, ChatMessageEvent> consumer, java.util.Map<TopicPartition, Long> endOffsets) {
        return endOffsets.entrySet()
                .stream()
                .anyMatch(entry -> consumer.position(entry.getKey()) < entry.getValue());
    }

    private DltMessageResponse toResponse(ConsumerRecord<String, ChatMessageEvent> record) {
        ChatMessageEvent event = record.value();
        return new DltMessageResponse(
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                event.messageId(),
                event.roomId(),
                event.roomName(),
                event.senderEmail(),
                event.senderName(),
                event.createdAt()
        );
    }

    private Properties consumerProperties() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "kafka-talk-dlt-replay-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        properties.put(JsonDeserializer.TRUSTED_PACKAGES, "com.kafka.auth.chat");
        properties.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ChatMessageEvent.class.getName());
        properties.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return properties;
    }

    private int normalizeLimit(int limit) {
        if (limit < 1) {
            return 20;
        }
        return Math.min(limit, 100);
    }

    private Set<String> normalizeMessageIds(List<String> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new HashSet<>();
        messageIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(normalized::add);
        return normalized;
    }

    private record DltRecord(
            DltMessageResponse response,
            ChatMessageEvent event
    ) {
    }
}
