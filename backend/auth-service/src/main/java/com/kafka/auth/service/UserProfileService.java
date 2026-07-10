package com.kafka.auth.service;

import com.kafka.auth.dto.AuthDtos.UpdateProfileRequest;
import com.kafka.auth.dto.AuthDtos.UserProfileHistoryResponse;
import com.kafka.auth.dto.AuthDtos.UserProfileResponse;
import com.kafka.auth.model.UserAccount;
import com.kafka.auth.model.UserProfileHistory;
import com.kafka.auth.repository.UserAccountRepository;
import com.kafka.auth.repository.UserProfileHistoryRepository;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.kafka.auth.storage.ObjectStorageService;
import com.kafka.auth.storage.StorageUrlSigner;
import com.kafka.auth.storage.StoredObject;

@Service
public class UserProfileService {
    private static final long MAX_PROFILE_IMAGE_BYTES = 5 * 1024 * 1024;

    private final UserAccountRepository userAccountRepository;
    private final UserProfileHistoryRepository userProfileHistoryRepository;
    private final ObjectStorageService objectStorageService;
    private final StorageUrlSigner storageUrlSigner;

    public UserProfileService(
            UserAccountRepository userAccountRepository,
            UserProfileHistoryRepository userProfileHistoryRepository,
            ObjectStorageService objectStorageService,
            StorageUrlSigner storageUrlSigner
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userProfileHistoryRepository = userProfileHistoryRepository;
        this.objectStorageService = objectStorageService;
        this.storageUrlSigner = storageUrlSigner;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse me(UserAccount user) {
        return toProfileResponse(user);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse publicProfile(Long userId) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return toProfileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(UpdateProfileRequest request, UserAccount user) {
        user.updateProfile(request.name().trim(), normalizeStatusMessage(request.statusMessage()));
        UserAccount saved = userAccountRepository.save(user);
        appendHistory(saved, "PROFILE_UPDATED");
        return toProfileResponse(saved);
    }

    @Transactional
    public UserProfileResponse updateTheme(String theme, UserAccount user) {
        user.updateTheme(theme);
        UserAccount saved = userAccountRepository.save(user);
        return toProfileResponse(saved);
    }

    @Transactional
    public UserProfileResponse updateProfileImage(MultipartFile file, UserAccount user) {
        String imageUrl = storeProfileImage(file);
        user.updateProfileImage(imageUrl);
        UserAccount saved = userAccountRepository.save(user);
        appendHistory(saved, "PROFILE_IMAGE_UPDATED");
        return toProfileResponse(saved);
    }

    public StoredObject loadProfileImage(String fileName) {
        return objectStorageService.load("profiles/" + Paths.get(fileName).getFileName());
    }

    private String storeProfileImage(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("프로필 이미지 파일이 비어 있습니다.");
        }
        if (file.getSize() > MAX_PROFILE_IMAGE_BYTES) {
            throw new IllegalArgumentException("프로필 이미지는 5MB 이하만 가능합니다.");
        }
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        if (!contentType.startsWith("image/")) {
            throw new IllegalArgumentException("프로필 이미지는 이미지 파일만 등록할 수 있습니다.");
        }

        String storedName = UUID.randomUUID() + extension(file.getOriginalFilename(), contentType);
        objectStorageService.store("profiles/" + storedName, file, contentType);
        return "/api/users/profile-images/" + storedName;
    }

    private void appendHistory(UserAccount user, String eventType) {
        userProfileHistoryRepository.save(new UserProfileHistory(
                user.getId(),
                user.getName(),
                user.getStatusMessage(),
                user.getProfileImageUrl(),
                eventType
        ));
    }

    private UserProfileResponse toProfileResponse(UserAccount user) {
        List<UserProfileHistoryResponse> history = userProfileHistoryRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, 20))
                .stream()
                .map(this::toHistoryResponse)
                .toList();
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getProvider().name(),
                user.getStatusMessage(),
                storageUrlSigner.sign(user.getProfileImageUrl()),
                user.getTheme(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                history
        );
    }

    private UserProfileHistoryResponse toHistoryResponse(UserProfileHistory history) {
        return new UserProfileHistoryResponse(
                history.getId(),
                history.getName(),
                history.getStatusMessage(),
                storageUrlSigner.sign(history.getProfileImageUrl()),
                history.getEventType(),
                history.getCreatedAt()
        );
    }

    private String normalizeStatusMessage(String statusMessage) {
        if (statusMessage == null || statusMessage.isBlank()) {
            return "";
        }
        return statusMessage.trim();
    }

    private String extension(String fileName, String contentType) {
        String cleaned = fileName == null || fileName.isBlank()
                ? "profile"
                : Paths.get(fileName).getFileName().toString();
        int dotIndex = cleaned.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < cleaned.length() - 1) {
            return cleaned.substring(dotIndex).toLowerCase(Locale.ROOT);
        }
        return switch (contentType) {
            case "image/gif" -> ".gif";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
