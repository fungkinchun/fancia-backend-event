package com.fancia.backend.event

import org.mockito.Mockito.mock
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.kafka.KafkaContainer
import org.wiremock.integrations.testcontainers.WireMockContainer
import software.amazon.awssdk.services.s3.S3Client

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
            registry.add("spring.cloud.openfeign.client.config.common-internal-service.url") {
                wiremock.baseUrl
            }
        }
    }

    @Bean
    fun jwtDecoder(): JwtDecoder = mock()

    @Bean
    @Primary
    fun s3Client(): S3Client = mock(S3Client::class.java)

    @Bean
    fun testProperties(): DynamicPropertyRegistrar =
        DynamicPropertyRegistrar { registry ->
            registry.add("spring.jpa.hibernate.ddl-auto") { "none" }
            registry.add("spring.flyway.enabled") { "true" }
            registry.add("spring.cloud.aws.secretsmanager.enabled") { "false" }
            registry.add("spring.cloud.aws.region.static") { "us-east-1" }
            registry.add("spring.kafka.listener.auto-startup") { "false" }
            registry.add("spring.kafka.admin.auto-create") { "false" }
            registry.add("spring.autoconfigure.exclude") {
                listOf(
                    "io.awspring.cloud.autoconfigure.core.AwsAutoConfiguration",
                    "io.awspring.cloud.autoconfigure.core.CredentialsProviderAutoConfiguration",
                    "io.awspring.cloud.autoconfigure.s3.S3AutoConfiguration",
                    "io.awspring.cloud.autoconfigure.secretsmanager.AwsSecretsManagerAutoConfiguration",
                ).joinToString(",")
            }
        }
}