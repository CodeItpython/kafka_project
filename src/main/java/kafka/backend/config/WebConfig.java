package kafka.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:./uploads/");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 모든 경로에 대해 CORS 허용
                // Vercel에서 배포된 프론트엔드의 실제 도메인을 여기에 추가합니다.
                // 예: "https://your-vercel-app.vercel.app"
                // 로컬 개발을 위해 "http://localhost:5173"도 포함하는 것이 좋습니다.
                .allowedOrigins(
                    "https://kafkaproj-guns-projects-3aacdd9d.vercel.app", // Vercel 프론트엔드 도메인
                    "http://localhost:5173", // 로컬 개발 환경
                    "https://kafkaproj.vercel.app"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 허용할 HTTP 메서드
                .allowedHeaders("*") // 모든 헤더 허용
                .allowCredentials(true); // 자격 증명(쿠키, HTTP 인증 등) 허용
    }
}
