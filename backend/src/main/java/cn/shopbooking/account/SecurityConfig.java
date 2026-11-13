package cn.shopbooking.account;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    HttpSessionSecurityContextRepository contextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, HttpSessionSecurityContextRepository repository) throws Exception {
        http.securityContext(context -> context.securityContextRepository(repository))
                .csrf(csrf -> csrf.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .requestCache(cache -> cache.disable())
                .logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) ->
                                json(response, 401, "LOGIN_REQUIRED", "请先登录。"))
                        .accessDeniedHandler((request, response, error) ->
                                json(response, 403, "FORBIDDEN", "当前操作不可用，请刷新或重新登录。")))
                .authorizeHttpRequests(routes -> routes
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/api/v1/auth/csrf", "/api/v1/public/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/accept", "/api/v1/public/*/bookings").permitAll()
                        .requestMatchers("/api/v1/auth/me").authenticated()
                        .requestMatchers("/api/v1/owner/**").hasRole("OWNER")
                        .requestMatchers("/api/v1/employee/**").hasRole("EMPLOYEE")
                        .anyRequest().denyAll());
        return http.build();
    }

    private static void json(HttpServletResponse response, int status, String code, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().printf("{\"code\":\"%s\",\"message\":\"%s\",\"requestId\":\"%s\"}",
                code, message, MDC.get("requestId"));
    }
}
