package kafka.backend.model;

import jakarta.persistence.*;

import java.io.Serializable;

@Entity
@Table(name = "friends")
@IdClass(Friend.FriendId.class)
public class Friend {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "friend_id")
    private Long friendId;

    // Getters and Setters
    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getFriendId() {
        return friendId;
    }

    public void setFriendId(Long friendId) {
        this.friendId = friendId;
    }

    // Composite primary key class
    public static class FriendId implements Serializable {
        private Long userId;
        private Long friendId;

        public FriendId() {}

        public FriendId(Long userId, Long friendId) {
            this.userId = userId;
            this.friendId = friendId;
        }

        // equals and hashCode
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FriendId friendId1 = (FriendId) o;
            if (!userId.equals(friendId1.userId)) return false;
            return friendId.equals(friendId1.friendId);
        }

        @Override
        public int hashCode() {
            int result = userId.hashCode();
            result = 31 * result + friendId.hashCode();
            return result;
        }
    }
}
