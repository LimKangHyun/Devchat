package project.backend.global.webconfig;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Value("${url.react-url}")
	private String reactUrl;

	@Value("${url.image-url}")
	private String imageUrl;

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**") // 모든 경로 허용
			.allowedOrigins(reactUrl, imageUrl) // React dev server
			.allowedMethods("*")
			.allowedHeaders("*")
			.allowCredentials(true);
	}

}

