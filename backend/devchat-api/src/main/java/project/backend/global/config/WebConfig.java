package project.backend.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Value("${url.domain-url}")
	private String domainUrl;

	@Value("${url.image-url}")
	private String imageUrl;

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**") // 모든 경로 허용
			.allowedOrigins(domainUrl, imageUrl)
			.allowedMethods("*")
			.allowedHeaders("*")
			.allowCredentials(true);
	}

}

