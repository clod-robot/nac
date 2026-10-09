package com.his.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置：无状态 JWT，所有端点放行由网关统一鉴权，
 * 本服务仅提供登录/验证码接口，密码使用 BCrypt 存储。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .cors().and()
                .sessionManagement().disable()
                .authorizeRequests()
                .antMatchers("/api/auth/**", "/actuator/**").permitAll()
                .anyRequest().authenticated()
                .and()
                .headers().frameOptions().disable();
        return http.build();
    }
}
