package kafka.backend.service;

import kafka.backend.model.Friend;
import kafka.backend.model.User;
import kafka.backend.repository.FriendRepository;
import kafka.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FriendService {

    @Autowired
    private FriendRepository friendRepository;

    @Autowired
    private UserRepository userRepository;

    @Transactional
    public Friend addFriend(Long userId, Long friendId) {
        if (userId.equals(friendId)) {
            throw new IllegalArgumentException("Cannot add self as a friend");
        }

        Optional<User> userOptional = userRepository.findById(userId);
        Optional<User> friendOptional = userRepository.findById(friendId);

        if (userOptional.isEmpty() || friendOptional.isEmpty()) {
            throw new IllegalArgumentException("User or friend not found");
        }

        Friend friend = new Friend();
        friend.setUserId(userId);
        friend.setFriendId(friendId);

        // Check if already friends (unidirectional for now, can be extended to bidirectional)
        if (friendRepository.findById(new Friend.FriendId(userId, friendId)).isPresent()) {
            throw new IllegalArgumentException("Already friends");
        }

        return friendRepository.save(friend);
    }

    @Transactional
    public void removeFriend(Long userId, Long friendId) {
        friendRepository.deleteByUserIdAndFriendId(userId, friendId);
    }

    public List<User> getFriends(Long userId) {
        // Fetch all users and filter out the current user
        return userRepository.findAll().stream()
                .filter(user -> !user.getId().equals(userId))
                .collect(Collectors.toList());
    }
}
