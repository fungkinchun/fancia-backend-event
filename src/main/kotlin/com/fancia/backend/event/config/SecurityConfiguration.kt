package com.fancia.backend.event.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain

@EnableMethodSecurity
@EnableWebSecurity
@Configuration
class SecurityConfiguration {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.authorizeHttpRequests { customizer ->
            customizer.requestMatchers(HttpMethod.GET, "/internal/v1/events/*/occurrences/*/reservations/*/checkout").permitAll()
            customizer.requestMatchers(HttpMethod.POST, "/internal/v1/events/*/occurrences/*/reservations/*/paid").permitAll()
            customizer.requestMatchers(HttpMethod.DELETE, "/internal/v1/users/*/resources").permitAll()
            customizer.requestMatchers("/api/v1/blocked", "/api/v1/blocked/**").authenticated()
            customizer.requestMatchers("/api/v1/reports", "/api/v1/reports/**").authenticated()
            customizer.requestMatchers(HttpMethod.GET, "/api/v1/events/me/saved").authenticated()
            customizer.requestMatchers(HttpMethod.GET, "/api/**").permitAll()
            customizer.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
            customizer.requestMatchers("/actuator/**").permitAll()
            customizer.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            customizer.anyRequest().authenticated()
        }.oauth2ResourceServer { oauth2ResourceServer ->
            oauth2ResourceServer.jwt(Customizer.withDefaults())
        }.csrf { it.disable() }
        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}