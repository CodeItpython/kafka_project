package com.kafka.chat.client;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A read-only projection of a user, fetched from auth-service over REST. chat-service
 * does not own the user table, so this is the only shape of "other user" it sees.
 * Getter names mirror the old UserAccount so call sites stay familiar.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserView {
    private Long id;
    private String email;
    private String name;
    private String provider;
    private String statusMessage;
    private String profileImageUrl;
}
