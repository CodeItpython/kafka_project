package com.kafka.auth.controller;

import com.kafka.auth.dto.AuthDtos.UpdateNotificationSettingsRequest;
import com.kafka.auth.dto.AuthDtos.UpdateProfileRequest;
import com.kafka.auth.dto.AuthDtos.UpdateThemeRequest;
import com.kafka.auth.dto.AuthDtos.UserProfileResponse;
import com.kafka.auth.model.UserAccount;
import com.kafka.auth.service.UserProfileService;
import com.kafka.auth.storage.StorageUrlSigner;
import com.kafka.auth.storage.StoredObject;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Validated
public class UserProfileController {
    private final UserProfileService userProfileService;
    private final StorageUrlSigner storageUrlSigner;

    @GetMapping("/me/profile")
    public ResponseEntity<UserProfileResponse> me(@AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.ok(userProfileService.me(user));
    }

    @PatchMapping("/me/profile")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal UserAccount user
    ) {
        return ResponseEntity.ok(userProfileService.updateProfile(request, user));
    }

    @PatchMapping("/me/theme")
    public ResponseEntity<UserProfileResponse> updateTheme(
            @Valid @RequestBody UpdateThemeRequest request,
            @AuthenticationPrincipal UserAccount user
    ) {
        return ResponseEntity.ok(userProfileService.updateTheme(request.theme(), user));
    }

    @PatchMapping("/me/notification-settings")
    public ResponseEntity<UserProfileResponse> updateNotificationSettings(
            @Valid @RequestBody UpdateNotificationSettingsRequest request,
            @AuthenticationPrincipal UserAccount user
    ) {
        return ResponseEntity.ok(userProfileService.updateNotificationSettings(request, user));
    }

    @PostMapping(path = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserProfileResponse> updateProfileImage(
            @RequestParam MultipartFile file,
            @AuthenticationPrincipal UserAccount user
    ) {
        return ResponseEntity.ok(userProfileService.updateProfileImage(file, user));
    }

    @GetMapping("/{userId}/profile")
    public ResponseEntity<UserProfileResponse> publicProfile(@PathVariable Long userId) {
        return ResponseEntity.ok(userProfileService.publicProfile(userId));
    }

    @GetMapping("/profile-images/{fileName:.+}")
    public ResponseEntity<Resource> profileImage(
            @PathVariable String fileName,
            @RequestParam(required = false) Long exp,
            @RequestParam(required = false) String sig
    ) {
        if (!storageUrlSigner.isValid("/api/users/profile-images/" + fileName, exp, sig)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        StoredObject storedObject = userProfileService.loadProfileImage(fileName);
        String contentType = storedObject.contentType();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType)
                .body(storedObject.resource());
    }
}
