package com.kafka.signaling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * WebRTC 1:1 video-call signaling service. This is a stateless STOMP relay: it
 * authenticates each socket with the shared auth-service JWT and forwards call
 * signals (invite/accept/offer/answer/ice/hangup) to the target peer. It keeps no
 * database, Kafka, or media — the actual audio/video is peer-to-peer WebRTC.
 */
@SpringBootApplication
public class SignalingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SignalingServiceApplication.class, args);
    }
}
