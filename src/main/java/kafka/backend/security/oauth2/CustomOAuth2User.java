package kafka.backend.security.oauth2;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.Collection;
import java.util.Map;

public class CustomOAuth2User extends DefaultOAuth2User {

    private String username;
    @Getter
    private Long userId;

    public CustomOAuth2User(Collection<? extends GrantedAuthority> authorities, Map<String, Object> attributes, String nameAttributeKey, String username, Long userId) {
        super(authorities, attributes, nameAttributeKey);
        this.username = username;
        this.userId = userId;
    }

    @Override
    public String getName() {
        return username;
    }


}

