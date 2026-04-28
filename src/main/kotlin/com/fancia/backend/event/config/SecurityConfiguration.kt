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
            customizer.requestMatchers(HttpMethod.GET, "/api/events").permitAll()
            customizer.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
            customizer.requestMatchers("/actuator/**").permitAll()
            customizer.anyRequest().authenticated()
        }.oauth2ResourceServer { oauth2ResourceServer ->
            oauth2ResourceServer.jwt(Customizer.withDefaults())
        }.csrf { it.disable() }
        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}