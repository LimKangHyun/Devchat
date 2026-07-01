package project.api.global.security.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import project.api.auth.app.CustomOAuth2UserService;
import project.api.global.security.entrypoint.RestAuthenticationEntryPoint;
import project.api.global.security.filter.JwtAuthenticationFilter;
import project.api.global.security.handler.form.FormFailureHandler;
import project.api.global.security.handler.form.FormSuccessHandler;
import project.api.global.security.handler.oauth.OAuth2FailureHandler;
import project.api.global.security.handler.oauth.OAuth2SuccessHandler;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

	private final FormFailureHandler formFailureHandler;
	private final FormSuccessHandler formSuccessHandler;
	private final OAuth2SuccessHandler oAuth2SuccessHandler;
	private final OAuth2FailureHandler oAuth2FailureHandler;
	private final CustomOAuth2UserService oAuth2UserService;
	private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
	private final JwtAuthenticationFilter jwtAuthenticationFilter;

	@Bean
	public PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.httpBasic(AbstractHttpConfigurer::disable)
				.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.sessionManagement(session ->
						session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

				.formLogin(form -> form
						.loginPage("/login")
						.usernameParameter("username")
						.passwordParameter("password")
						.failureHandler(formFailureHandler)
						.successHandler(formSuccessHandler)
						.permitAll())

				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/signup", "/login", "/login/oauth2/**", "/error")
						.anonymous()
						.requestMatchers("/token/**", "/logout", "/images/**",
								"/actuator/health", "/actuator/prometheus", "/ws",
								"/github/webhook/**")
						.permitAll()
						.anyRequest()
						.authenticated())

				.oauth2Login(oauth -> {
					oauth.successHandler(oAuth2SuccessHandler);
					oauth.failureHandler(oAuth2FailureHandler);
					oauth.userInfoEndpoint(userInfo ->
							userInfo.userService(oAuth2UserService));
				})

				.logout(AbstractHttpConfigurer::disable)

				.exceptionHandling(exception ->
						exception.authenticationEntryPoint(restAuthenticationEntryPoint))

				.addFilterBefore(jwtAuthenticationFilter, ExceptionTranslationFilter.class)
				.build();
	}
}