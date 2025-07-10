package kafka.backend.service;

import kafka.backend.model.User;
import kafka.backend.repository.UserRepository;
import kafka.backend.security.JwtTokenProvider; // JwtTokenProvider 임포트 추가
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap; // HashMap 임포트 추가
import java.util.Map; // Map 임포트 추가
import java.util.Optional;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider; // JwtTokenProvider 주입

    public User registerUser(String username, String email, String password) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already exists ");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        return userRepository.save(user);
    }

    public Optional<Map<String, Object>> validateUser(String username, String password) { // 반환 타입 변경
        Optional<User> userOptional = userRepository.findByUsername(username);
        if (userOptional.isPresent() && passwordEncoder.matches(password, userOptional.get().getPassword())) {
            User user = userOptional.get();

            // Authentication 객체 생성 (여기서는 간단하게 ROLE_USER 권한을 부여)
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    new org.springframework.security.core.userdetails.User(user.getUsername(), user.getPassword(), Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))),
                    null
            );

            String jwt = tokenProvider.generateToken(authentication); // Authentication 객체 전달

            // Fetch the user again to ensure the ID is populated
            Optional<User> fetchedUserOptional = userRepository.findById(user.getId());
            if (fetchedUserOptional.isEmpty()) {
                return Optional.empty(); // Should not happen
            }
            User fetchedUser = fetchedUserOptional.get();

            Map<String, Object> response = new HashMap<>();
            response.put("user", fetchedUser);
            response.put("accessToken", jwt);
            return Optional.of(response);
        }
        return Optional.empty();
    }
}
