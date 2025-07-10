package kafka.backend.controller;

import kafka.backend.model.User;
import kafka.backend.service.FriendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    @Autowired
    private FriendService friendService;

    @PostMapping("/add")
    public ResponseEntity<?> addFriend(@RequestBody Map<String, Long> payload) {
        Long userId = payload.get("userId");
        Long friendId = payload.get("friendId");

        if (userId == null || friendId == null) {
            return new ResponseEntity<>("User ID and Friend ID are required", HttpStatus.BAD_REQUEST);
        }

        try {
            friendService.addFriend(userId, friendId);
            return new ResponseEntity<>("Friend added successfully", HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @DeleteMapping("/remove")
    public ResponseEntity<?> removeFriend(@RequestBody Map<String, Long> payload) {
        Long userId = payload.get("userId");
        Long friendId = payload.get("friendId");

        if (userId == null || friendId == null) {
            return new ResponseEntity<>("User ID and Friend ID are required", HttpStatus.BAD_REQUEST);
        }

        friendService.removeFriend(userId, friendId);
        return new ResponseEntity<>("Friend removed successfully", HttpStatus.OK);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<User>> getFriends(@PathVariable Long userId) {
        List<User> friends = friendService.getFriends(userId);
        return new ResponseEntity<>(friends, HttpStatus.OK);
    }
}
