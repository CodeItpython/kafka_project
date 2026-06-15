package com.kafka.auth.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka.auth.chat.dto.ChatDtos.ConversationSummaryResponse;
import com.kafka.auth.chat.model.ChatMessageDocument;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ChatSummaryService {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String responsesUrl;

    public ChatSummaryService(
            ObjectMapper objectMapper,
            @Value("${app.openai.api-key}") String apiKey,
            @Value("${app.openai.model}") String model,
            @Value("${app.openai.responses-url}") String responsesUrl
    ) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.responsesUrl = responsesUrl;
    }

    public ConversationSummaryResponse summarize(List<ChatMessageDocument> messages) {
        List<ChatMessageDocument> visibleMessages = messages.stream()
                .filter(message -> !message.isDeletedForEveryone())
                .filter(message -> message.getContent() != null && !message.getContent().isBlank())
                .limit(80)
                .toList();
        if (visibleMessages.isEmpty()) {
            return new ConversationSummaryResponse("요약할 텍스트 메시지가 없습니다.", model, Instant.now(), 0);
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API 키가 설정되어 있지 않습니다.");
        }

        String transcript = transcript(visibleMessages);
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "instructions", """
                            너는 채팅 대화를 한국어로 간결하게 요약하는 도우미다.
                            답변은 다음 형식을 지켜라.
                            1. 핵심 요약 2~4문장
                            2. 결정/합의 사항
                            3. 다음 할 일
                            불확실한 내용은 추측하지 말고 대화에 나온 내용만 사용해라.
                            """,
                    "input", transcript,
                    "max_output_tokens", 500
            ));
            HttpRequest request = HttpRequest.newBuilder(URI.create(responsesUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("OpenAI summary request failed. status={} body={}", response.statusCode(), response.body());
                throw new IllegalStateException("대화 요약을 생성하지 못했습니다.");
            }
            return new ConversationSummaryResponse(extractSummary(response.body()), model, Instant.now(), visibleMessages.size());
        } catch (IOException exception) {
            log.warn("OpenAI summary request serialization failed.", exception);
            throw new IllegalStateException("대화 요약을 생성하지 못했습니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("대화 요약을 생성하지 못했습니다.");
        }
    }

    private String transcript(List<ChatMessageDocument> messages) {
        StringBuilder builder = new StringBuilder();
        for (ChatMessageDocument message : messages) {
            builder.append('[')
                    .append(message.getCreatedAt())
                    .append("] ")
                    .append(message.getSenderName())
                    .append(": ")
                    .append(message.getContent())
                    .append('\n');
        }
        return builder.toString();
    }

    private String extractSummary(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode outputText = root.get("output_text");
        if (outputText != null && outputText.isTextual() && !outputText.asText().isBlank()) {
            return outputText.asText();
        }

        JsonNode output = root.get("output");
        if (output != null && output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.get("content");
                if (content == null || !content.isArray()) {
                    continue;
                }
                for (JsonNode contentItem : content) {
                    JsonNode text = contentItem.get("text");
                    if (text != null && text.isTextual() && !text.asText().isBlank()) {
                        return text.asText();
                    }
                }
            }
        }
        throw new IllegalStateException("대화 요약을 생성하지 못했습니다.");
    }
}
