package kafka.backend.repository;

import kafka.backend.model.Friend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FriendRepository extends JpaRepository<Friend, Friend.FriendId> {
    List<Friend> findByUserId(Long userId);
    void deleteByUserIdAndFriendId(Long userId, Long friendId);
}
