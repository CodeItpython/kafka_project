package kafka.backend.security.oauth2;

import kafka.backend.model.User;
import kafka.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String userNameAttributeName = userRequest.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

        // Extract user attributes based on registrationId (Kakao in this case)
        String providerId = null;
        String email = null;
        String nickname = null;

        if ("kakao".equals(registrationId)) {
            Map<String, Object> kakaoAccount = oAuth2User.getAttribute("kakao_account");
            if (kakaoAccount != null) {
                providerId = String.valueOf((Long) oAuth2User.getAttribute(userNameAttributeName));
                email = (String) kakaoAccount.get("email");
                Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
                if (profile != null) {
                    nickname = (String) profile.get("nickname");
                }
            }
        }

        // Check if user already exists in our database
        Optional<User> existingUser = userRepository.findByProviderAndProviderId(registrationId, providerId);
        User user;

        if (existingUser.isPresent()) {
            user = existingUser.get();
            // Update user details if necessary
            user.setUsername(nickname != null ? nickname : (email != null ? email : providerId + "@" + registrationId + ".com")); // email이 null일 경우 임시 이메일 사용
            if (email != null) { // email이 제공될 경우에만 업데이트
                user.setEmail(email);
            }
        } else {
            // Create new user
            user = new User();
            user.setProvider(registrationId);
            user.setProviderId(providerId);
            user.setUsername(nickname != null ? nickname : (email != null ? email : providerId + "@" + registrationId + ".com")); // email이 null일 경우 임시 이메일 사용
            user.setEmail(email != null ? email : providerId + "@" + registrationId + ".com"); // email이 null일 경우 임시 이메일 사용
            user.setPassword("oauth2user"); // Set a dummy password for OAuth2 users
        }
        user = userRepository.save(user); // Assign the saved user back to ensure ID is populated

        return new CustomOAuth2User(oAuth2User.getAuthorities(), oAuth2User.getAttributes(), userNameAttributeName, user.getUsername(), user.getId());
    }
}