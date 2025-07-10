package kafka.backend.controller;

import kafka.backend.model.User;
import kafka.backend.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody Map<String, String> payload) {
        try {
            String username = payload.get("username");
            String email = payload.get("email");
            String password = payload.get("password");

            if (username == null || email == null || password == null) {
                return new ResponseEntity<>("Username, email, and password are required", HttpStatus.BAD_REQUEST);
            }

            User registeredUser = authService.registerUser(username, email, password);
            return new ResponseEntity<>(registeredUser, HttpStatus.CREATED);
        } catch (RuntimeException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.CONFLICT);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");

        if (username == null || password == null) {
            return new ResponseEntity<>("Username and password are required", HttpStatus.BAD_REQUEST);
        }

        return authService.validateUser(username, password)
                .map(responseMap -> { // Map으로 변경
                    User user = (User) responseMap.get("user");
                    String accessToken = (String) responseMap.get("accessToken");

                    Map<String, Object> response = new HashMap<>();
                    response.put("message", "Login successful");
                    response.put("userId", user.getId());
                    response.put("username", user.getUsername());
                    response.put("accessToken", accessToken); // 토큰 추가
                    return new ResponseEntity<>(response, HttpStatus.OK);
                })
                .orElse(new ResponseEntity<>(Map.of("message", "Invalid credentials"), HttpStatus.UNAUTHORIZED));
    }
}