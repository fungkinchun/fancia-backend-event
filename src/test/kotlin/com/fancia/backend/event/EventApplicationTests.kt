package com.fancia.backend.event

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(TestConfig::class)
class EventApplicationTests {
    @Test
    fun contextLoads() {
    }
}
