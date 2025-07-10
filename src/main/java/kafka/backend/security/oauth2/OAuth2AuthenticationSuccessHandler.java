package kafka.backend.security.oauth2;

import kafka.backend.security.JwtTokenProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;



@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        Long userId = null;
        String username = null;

        if (oAuth2User instanceof CustomOAuth2User) {
            CustomOAuth2User customOAuth2User = (CustomOAuth2User) oAuth2User;
            userId = customOAuth2User.getUserId();
            username = customOAuth2User.getName();
        } else {
            // Fallback for non-custom OAuth2 users if necessary
            username = String.valueOf(oAuth2User.getAttributes().get("id"));
            // You might need to fetch userId from your database here if it's not a CustomOAuth2User
        }

        // Generate JWT token
        String token = tokenProvider.generateToken(authentication);

        // Redirect to frontend with token and userId
        String redirectUrl = UriComponentsBuilder.fromUriString("http://localhost:5173/oauth2/redirect")
                .queryParam("token", token)
                .queryParam("userId", userId) // Pass the actual database userId
                .queryParam("username", URLEncoder.encode(username, StandardCharsets.UTF_8.toString())) // URL-encode the username
                .build().toUriString();

        System.out.println("Redirecting with userId: " + userId + ", username: " + username);

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
