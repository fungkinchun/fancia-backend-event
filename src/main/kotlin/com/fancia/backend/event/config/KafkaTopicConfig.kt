package com.fancia.backend.event.config

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration
class KafkaTopicConfig {
    @Bean
    fun eventsTopic(): NewTopic {
        return TopicBuilder.name("eventsTopic")
            .partitions(3)
            .replicas(1)
            .build()
    }
}