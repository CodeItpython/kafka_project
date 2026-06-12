package com.kafka.auth.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka.auth.chat.dto.ChatDtos.ChatRoomResponse;
import com.kafka.auth.chat.dto.ChatDtos.RoomPresenceResponse;
import com.kafka.auth.model.UserAccount;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatStateService {
    private static final Duration ROOM_CACHE_TTL = Duration.ofSeconds(30);
    private static final Duration ONLINE_TTL = Duration.ofSeconds(75);
    private static final Duration TYPING_TTL = Duration.ofSeconds(5);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public List<ChatRoomResponse> cachedRooms(String email, String query, Supplier<List<ChatRoomResponse>> loader) {
        String key = "cache:rooms:" + normalize(email) + ":" + normalize(query);
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<>() {
                });
            }

            List<ChatRoomResponse> rooms = loader.get();
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(rooms), ROOM_CACHE_TTL);
            return rooms;
        } catch (JsonProcessingException exception) {
            log.warn("Redis room cache serialization failed. Using source repository.", exception);
            return loader.get();
        } catch (RuntimeException exception) {
            log.warn("Redis room cache unavailable. Using source repository. reason={}", exception.getClass().getSimpleName());
            log.debug("Redis room cache failure detail.", exception);
            return loader.get();
        }
    }

    public void evictRoomCaches(String email) {
        deleteByPattern("cache:rooms:" + normalize(email) + ":*");
    }

    public void markOnline(UserAccount user) {
        try {
            redisTemplate.opsForValue().set(onlineKey(user.getEmail()), user.getName(), ONLINE_TTL);
        } catch (RuntimeException exception) {
            log.debug("Unable to update Redis online state.", exception);
        }
    }

    public boolean isOnline(String email) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(onlineKey(email)));
        } catch (RuntimeException exception) {
            log.debug("Unable to read Redis online state.", exception);
            return false;
        }
    }

    public void setTyping(String roomId, UserAccount user, boolean typing) {
        try {
            String key = typingKey(roomId, user.getEmail());
            if (typing) {
                redisTemplate.opsForValue().set(key, user.getName(), TYPING_TTL);
                return;
            }
            redisTemplate.delete(key);
        } catch (RuntimeException exception) {
            log.debug("Unable to update Redis typing state.", exception);
        }
    }

    public RoomPresenceResponse roomPresence(String roomId, Set<String> participantEmails, String currentUserEmail) {
        Set<String> onlineUsers = new LinkedHashSet<>();
        Set<String> typingUsers = new LinkedHashSet<>();
        try {
            participantEmails.stream()
                    .filter(email -> !email.equalsIgnoreCase(currentUserEmail))
                    .filter(this::isOnline)
                    .forEach(onlineUsers::add);

            Optional.ofNullable(redisTemplate.keys("state:typing:" + roomId + ":*"))
                    .orElse(Set.of())
                    .forEach(key -> {
                        String email = key.substring(key.lastIndexOf(':') + 1);
                        if (!email.equalsIgnoreCase(currentUserEmail)) {
                            String name = redisTemplate.opsForValue().get(key);
                            typingUsers.add(name == null || name.isBlank() ? email : name);
                        }
                    });
        } catch (RuntimeException exception) {
            log.debug("Unable to read Redis room presence.", exception);
        }
        return new RoomPresenceResponse(onlineUsers.stream().toList(), typingUsers.stream().toList());
    }

    private void deleteByPattern(String pattern) {
        try {
            Optional.ofNullable(redisTemplate.keys(pattern)).orElse(Set.of()).forEach(redisTemplate::delete);
        } catch (RuntimeException exception) {
            log.debug("Unable to evict Redis keys for pattern {}.", pattern, exception);
        }
    }

    private String onlineKey(String email) {
        return "state:online:" + normalize(email);
    }

    private String typingKey(String roomId, String email) {
        return "state:typing:" + roomId + ":" + normalize(email);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "_";
        }
        return value.trim().toLowerCase();
    }
}
