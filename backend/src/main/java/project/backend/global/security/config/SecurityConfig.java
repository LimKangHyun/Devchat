package project.backend.global.security.config;

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
import project.backend.auth.app.CustomOAuth2UserService;
import project.backend.global.security.handler.CustomLogoutSuccessHandler;
import project.backend.global.security.handler.form.FormFailureHandler;
import project.backend.global.security.handler.form.FormSuccessHandler;
import project.backend.global.security.filter.JwtAuthenticationFilter;
import project.backend.global.security.handler.oauth.OAuth2FailureHandler;
import project.backend.global.security.handler.oauth.OAuth2SuccessHandler;
import project.backend.global.security.entrypoint.RestAuthenticationEntryPoint;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

	private final FormFailureHandler formFailureHandler;
	private final FormSuccessHandler formSuccessHandler;

	private final OAuth2SuccessHandler oAuth2SuccessHandler;
	private final OAuth2FailureHandler oAuth2FailureHandler;
	private final CustomOAuth2UserService oAuth2UserService;

	private final CustomLogoutSuccessHandler logoutSuccessHandler;
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
			.sessionManagement(
				session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

			.formLogin(form -> {
				form.loginPage("/login")
					.usernameParameter("username")
					.passwordParameter("password")
					.failureHandler(formFailureHandler)
					.successHandler(formSuccessHandler)
					.permitAll();
			})

			.authorizeHttpRequests(auth -> {
				auth
					.requestMatchers("/signup", "/login", "/login/oauth2/**", "/error")
					.anonymous()

					.requestMatchers("/token/**", "/github/**", "/images/**")
					.permitAll()

					.anyRequest()
					.authenticated();
			})

			.oauth2Login(oauth -> {
				oauth.successHandler(oAuth2SuccessHandler);
				oauth.failureHandler(oAuth2FailureHandler);
				oauth.userInfoEndpoint(userInfoEndpoint -> {
					userInfoEndpoint.userService(oAuth2UserService);
				});
			})

			.logout(AbstractHttpConfigurer::disable)

			.exceptionHandling(exception ->
				exception.authenticationEntryPoint(restAuthenticationEntryPoint))

//			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
			.addFilterAfter(jwtAuthenticationFilter, ExceptionTranslationFilter.class)
			.build();

	}

}
