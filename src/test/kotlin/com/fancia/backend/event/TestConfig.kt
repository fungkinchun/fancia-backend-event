package com.fancia.backend.event

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.kafka.KafkaContainer
import org.wiremock.integrations.testcontainers.WireMockContainer

@TestConfiguration(proxyBeanMethods = false)
class TestConfig {
    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer<*> {
        return PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
    }

    @Bean
    @ServiceConnection
    fun kafka(): KafkaContainer {
        return KafkaContainer("apache/kafka-native:3.8.0")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
    }

    @Bean
    fun wiremock(): WireMockContainer {
        return WireMockContainer("wiremock/wiremock:3.12.0").apply {
            start()
        }
    }

    @Bean
    fun wiremockProperties(wiremock: WireMockContainer): DynamicPropertyRegistrar {
        return DynamicPropertyRegistrar { registry ->
            registry.add("spring.cloud.openfeign.client.config.common-service.url") {
                wiremock.baseUrl
            }
        }
    }
}