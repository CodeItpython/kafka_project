package kafka.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "kafka.backend.repository") // JPA 리포지토리 패키지 지정
@EnableElasticsearchRepositories(basePackages = "kafka.backend.repository") // Elasticsearch 리포지토리 패키지 지정
public class KafkaProjectBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaProjectBeApplication.class, args);
    }

}
