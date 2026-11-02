package project.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import project.ai.client.internal.InternalApiProperties;

@SpringBootApplication
@EnableConfigurationProperties(InternalApiProperties.class)
public class AiApplication {

    public static void main(String[] args) {

        SpringApplication.run(AiApplication.class, args);
    }
}
