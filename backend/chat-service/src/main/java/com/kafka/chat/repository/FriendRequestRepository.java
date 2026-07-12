package com.kafka.chat.repository;

import com.kafka.chat.model.FriendRequest;
import com.kafka.chat.model.FriendRequestStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {
    Optional<FriendRequest> findByRequesterEmailIgnoreCaseAndAddresseeEmailIgnoreCase(String requesterEmail, String addresseeEmail);

    List<FriendRequest> findByAddresseeEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(String addresseeEmail, FriendRequestStatus status);

    List<FriendRequest> findByRequesterEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(String requesterEmail, FriendRequestStatus status);

    /** ACCEPTED 상태에서 해당 이메일이 관련된(요청자 또는 수신자) 모든 친구 관계. */
    @Query("""
            select f from FriendRequest f
            where f.status = com.kafka.chat.model.FriendRequestStatus.ACCEPTED
              and (lower(f.requesterEmail) = lower(:email) or lower(f.addresseeEmail) = lower(:email))
            """)
    List<FriendRequest> findAcceptedInvolving(@Param("email") String email);
}
