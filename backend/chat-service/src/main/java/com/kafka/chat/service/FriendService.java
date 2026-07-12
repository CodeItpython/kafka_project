package com.kafka.chat.service;

import com.kafka.chat.client.UserDirectoryClient;
import com.kafka.chat.client.UserView;
import com.kafka.chat.dto.ChatDtos.ContactResponse;
import com.kafka.chat.dto.ChatDtos.FriendRequestResponse;
import com.kafka.chat.model.FriendRequest;
import com.kafka.chat.model.FriendRequestStatus;
import com.kafka.chat.repository.FriendRequestRepository;
import com.kafka.chat.security.AuthUser;
import com.kafka.chat.storage.StorageUrlSigner;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 친구 요청/친구 관계. auth-service가 사용자 테이블을 소유하므로 email 문자열을 키로 쓰고,
 * 이름·프로필은 UserDirectoryClient로 조회해 하이드레이션한다. ACCEPTED 행 = 친구 관계.
 */
@Service
@RequiredArgsConstructor
public class FriendService {
    private final FriendRequestRepository friendRequestRepository;
    private final UserDirectoryClient userDirectory;
    private final StorageUrlSigner storageUrlSigner;
    private final ChatStateService chatStateService;

    @Transactional
    public FriendRequestResponse sendRequest(AuthUser user, String targetEmailRaw) {
        String me = user.getEmail();
        UserView target = userDirectory.findByEmail(targetEmailRaw)
                .orElseThrow(() -> new IllegalArgumentException("상대를 찾을 수 없습니다."));
        String targetEmail = target.getEmail();
        if (targetEmail.equalsIgnoreCase(me)) {
            throw new IllegalArgumentException("자기 자신에게는 친구 요청을 보낼 수 없습니다.");
        }

        Optional<FriendRequest> mine = friendRequestRepository
                .findByRequesterEmailIgnoreCaseAndAddresseeEmailIgnoreCase(me, targetEmail);
        Optional<FriendRequest> theirs = friendRequestRepository
                .findByRequesterEmailIgnoreCaseAndAddresseeEmailIgnoreCase(targetEmail, me);

        boolean alreadyFriends = mine.map(f -> f.getStatus() == FriendRequestStatus.ACCEPTED).orElse(false)
                || theirs.map(f -> f.getStatus() == FriendRequestStatus.ACCEPTED).orElse(false);
        if (alreadyFriends) {
            throw new IllegalArgumentException("이미 친구입니다.");
        }
        // 상대가 이미 나에게 보냈다면 즉시 수락(상호 요청 → 친구).
        if (theirs.map(f -> f.getStatus() == FriendRequestStatus.PENDING).orElse(false)) {
            FriendRequest reverse = theirs.get();
            reverse.accept();
            friendRequestRepository.save(reverse);
            return toResponse(reverse, targetEmail, target, 0, "sent");
        }
        if (mine.map(f -> f.getStatus() == FriendRequestStatus.PENDING).orElse(false)) {
            throw new IllegalArgumentException("이미 친구 요청을 보냈습니다.");
        }
        FriendRequest saved = friendRequestRepository.save(new FriendRequest(me, targetEmail));
        return toResponse(saved, targetEmail, target, 0, "sent");
    }

    @Transactional
    public void accept(AuthUser user, Long requestId) {
        FriendRequest request = requirePending(requestId);
        if (!request.getAddresseeEmail().equalsIgnoreCase(user.getEmail())) {
            throw new IllegalArgumentException("수락할 수 있는 요청이 아닙니다.");
        }
        request.accept();
        friendRequestRepository.save(request);
    }

    @Transactional
    public void reject(AuthUser user, Long requestId) {
        FriendRequest request = requirePending(requestId);
        if (!request.getAddresseeEmail().equalsIgnoreCase(user.getEmail())) {
            throw new IllegalArgumentException("거절할 수 있는 요청이 아닙니다.");
        }
        friendRequestRepository.delete(request);
    }

    @Transactional
    public void cancel(AuthUser user, Long requestId) {
        FriendRequest request = requirePending(requestId);
        if (!request.getRequesterEmail().equalsIgnoreCase(user.getEmail())) {
            throw new IllegalArgumentException("취소할 수 있는 요청이 아닙니다.");
        }
        friendRequestRepository.delete(request);
    }

    @Transactional(readOnly = true)
    public List<FriendRequestResponse> received(AuthUser user) {
        String me = user.getEmail();
        Set<String> myFriends = friendEmails(me);
        List<FriendRequest> requests = friendRequestRepository
                .findByAddresseeEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(me, FriendRequestStatus.PENDING);
        Map<String, UserView> directory = hydrate(requests.stream().map(FriendRequest::getRequesterEmail).toList());
        return requests.stream()
                .map(request -> toResponse(
                        request,
                        request.getRequesterEmail(),
                        directory.get(request.getRequesterEmail().toLowerCase(Locale.ROOT)),
                        mutualCount(myFriends, request.getRequesterEmail()),
                        "received"))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FriendRequestResponse> sent(AuthUser user) {
        String me = user.getEmail();
        List<FriendRequest> requests = friendRequestRepository
                .findByRequesterEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(me, FriendRequestStatus.PENDING);
        Map<String, UserView> directory = hydrate(requests.stream().map(FriendRequest::getAddresseeEmail).toList());
        return requests.stream()
                .map(request -> toResponse(
                        request,
                        request.getAddresseeEmail(),
                        directory.get(request.getAddresseeEmail().toLowerCase(Locale.ROOT)),
                        0,
                        "sent"))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ContactResponse> friends(AuthUser user) {
        Set<String> emails = friendEmails(user.getEmail());
        Map<String, UserView> directory = hydrate(emails);
        return emails.stream()
                .map(email -> directory.get(email.toLowerCase(Locale.ROOT)))
                .filter(view -> view != null)
                .sorted(Comparator.comparing(UserView::getName, Comparator.nullsLast(String::compareTo)))
                .map(view -> new ContactResponse(
                        view.getId(),
                        view.getEmail(),
                        view.getName(),
                        view.getProvider(),
                        view.getStatusMessage(),
                        storageUrlSigner.sign(view.getProfileImageUrl()),
                        chatStateService.isOnline(view.getEmail())))
                .toList();
    }

    private FriendRequest requirePending(Long requestId) {
        FriendRequest request = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("요청을 찾을 수 없습니다."));
        if (request.getStatus() != FriendRequestStatus.PENDING) {
            throw new IllegalArgumentException("이미 처리된 요청입니다.");
        }
        return request;
    }

    /** 해당 이메일의 친구(ACCEPTED 상대) 이메일 집합(소문자). */
    private Set<String> friendEmails(String email) {
        return friendRequestRepository.findAcceptedInvolving(email).stream()
                .map(request -> request.getRequesterEmail().equalsIgnoreCase(email)
                        ? request.getAddresseeEmail()
                        : request.getRequesterEmail())
                .map(other -> other.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private int mutualCount(Set<String> myFriends, String otherEmail) {
        if (myFriends.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(friendEmails(otherEmail));
        intersection.retainAll(myFriends);
        return intersection.size();
    }

    private Map<String, UserView> hydrate(java.util.Collection<String> emails) {
        return userDirectory.findByEmails(emails).stream()
                .collect(Collectors.toMap(view -> view.getEmail().toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a));
    }

    private FriendRequestResponse toResponse(FriendRequest request, String otherEmail, UserView view, int mutual, String direction) {
        String name = view != null ? view.getName() : otherEmail;
        String avatar = view != null ? storageUrlSigner.sign(view.getProfileImageUrl()) : null;
        String status = view != null ? view.getStatusMessage() : null;
        return new FriendRequestResponse(
                request.getId(),
                otherEmail,
                name,
                avatar,
                status,
                mutual,
                chatStateService.isOnline(otherEmail),
                direction,
                request.getCreatedAt());
    }
}
