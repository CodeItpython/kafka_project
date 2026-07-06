package com.kafka.chat.service;

import com.kafka.chat.dto.ChatMessageEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatMetricsService {
    private final MeterRegistry meterRegistry;

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordKafkaPublishSuccess(Timer.Sample sample, ChatMessageEvent event) {
        stop(sample, "kafka.talk.kafka.publish.duration", "success", "has_attachment", hasAttachment(event));
        count("kafka.talk.kafka.publish.total", "success", "has_attachment", hasAttachment(event));
    }

    public void recordKafkaPublishFailure(Timer.Sample sample, ChatMessageEvent event) {
        stop(sample, "kafka.talk.kafka.publish.duration", "failure", "has_attachment", hasAttachment(event));
        count("kafka.talk.kafka.publish.total", "failure", "has_attachment", hasAttachment(event));
    }

    public void recordKafkaConsumeSuccess(Timer.Sample sample, ChatMessageEvent event) {
        stop(sample, "kafka.talk.kafka.consume.duration", "success", "has_attachment", hasAttachment(event));
        count("kafka.talk.kafka.consume.total", "success", "has_attachment", hasAttachment(event));
    }

    public void recordKafkaConsumeFailure(Timer.Sample sample, ChatMessageEvent event) {
        stop(sample, "kafka.talk.kafka.consume.duration", "failure", "has_attachment", hasAttachment(event));
        count("kafka.talk.kafka.consume.total", "failure", "has_attachment", hasAttachment(event));
    }

    public void recordKafkaDlt(ChatMessageEvent event) {
        count("kafka.talk.kafka.dlt.total", "failure", "has_attachment", hasAttachment(event));
    }

    public void recordElasticsearchIndexSuccess(Timer.Sample sample) {
        stop(sample, "kafka.talk.elasticsearch.index.duration", "success");
        count("kafka.talk.elasticsearch.index.total", "success");
    }

    public void recordElasticsearchIndexFailure(Timer.Sample sample) {
        stop(sample, "kafka.talk.elasticsearch.index.duration", "failure");
        count("kafka.talk.elasticsearch.index.total", "failure");
    }

    public void recordWebSocketBroadcastSuccess(Timer.Sample sample) {
        stop(sample, "kafka.talk.websocket.broadcast.duration", "success");
        count("kafka.talk.websocket.broadcast.total", "success");
    }

    public void recordWebSocketBroadcastFailure(Timer.Sample sample) {
        stop(sample, "kafka.talk.websocket.broadcast.duration", "failure");
        count("kafka.talk.websocket.broadcast.total", "failure");
    }

    public <T> T recordSearch(String source, Supplier<T> action) {
        Timer.Sample sample = startTimer();
        try {
            T result = action.get();
            stop(sample, "kafka.talk.search.duration", "success", "source", source);
            count("kafka.talk.search.total", "success", "source", source);
            return result;
        } catch (RuntimeException exception) {
            stop(sample, "kafka.talk.search.duration", "failure", "source", source);
            count("kafka.talk.search.total", "failure", "source", source);
            throw exception;
        }
    }

    private String hasAttachment(ChatMessageEvent event) {
        return event.attachmentUrl() == null || event.attachmentUrl().isBlank() ? "false" : "true";
    }

    private void stop(Timer.Sample sample, String metricName, String result) {
        sample.stop(Timer.builder(metricName)
                .tag("result", result)
                .register(meterRegistry));
    }

    private void stop(Timer.Sample sample, String metricName, String result, String tagName, String tagValue) {
        sample.stop(Timer.builder(metricName)
                .tag("result", result)
                .tag(tagName, tagValue)
                .register(meterRegistry));
    }

    private void count(String metricName, String result) {
        Counter.builder(metricName)
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }

    private void count(String metricName, String result, String tagName, String tagValue) {
        Counter.builder(metricName)
                .tag("result", result)
                .tag(tagName, tagValue)
                .register(meterRegistry)
                .increment();
    }
}
