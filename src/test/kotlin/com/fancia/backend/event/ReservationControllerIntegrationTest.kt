package com.fancia.backend.event

import com.fancia.backend.event.core.dto.ReservationResponse
import com.fancia.backend.event.core.entity.Event
import com.fancia.backend.event.core.entity.Reservation
import com.fancia.backend.event.core.repository.ReservationRepository
import com.fancia.backend.event.mapper.ReservationMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.util.*

@SpringBootTest(classes = [EventApplication::class])
@AutoConfigureMockMvc
@Testcontainers
@Import(TestConfig::class)
class ReservationControllerIntegrationTest(
    private val mockMvc: MockMvc,
    private val reservationRepository: ReservationRepository,
    private val objectMapper: JsonMapper,
    private val reservationMapper: ReservationMapper
) : FunSpec({
    test("should create a new event") {
        val testUserId = UUID.randomUUID()
        val testInterestGroupId = UUID.randomUUID()
        val response = mockMvc
            .post("/api/events") {
                with(jwt().jwt {
                    it.claim("userId", testUserId)
                })
                val requestBody = mapOf(
                    "name" to "testEvent",
                    "description" to "string",
                    "startTime" to "2024-06-01T10:00:00",
                    "duration" to "PT2H",
                    "interestGroupId" to testInterestGroupId,
                    "tags" to listOf("good")
                )
                content = objectMapper.writeValueAsString(requestBody)
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.name", `is`("testEvent"))
                jsonPath("$.id", `is`(notNullValue()))
            }
        val createdEvent = response.toEvent(objectMapper)
        val createReservationResponse = mockMvc
            .post("/api/reservations") {
                with(jwt().jwt {
                    it.claim("userId", testUserId)
                })
                val requestBody = mapOf(
                    "eventId" to createdEvent.id,
                    "guests" to 0,
                    "payload" to objectMapper.writeValueAsString(mapOf("extraInfo" to "string")),
                )
                content = objectMapper.writeValueAsString(requestBody)
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.eventId", `is`(createdEvent.id.toString()))
                jsonPath("$.userId", `is`(testUserId.toString()))
                jsonPath("$.status", `is`("PENDING"))
            }
        val found = reservationRepository.existsByIdEventIdAndIdUserId(createdEvent.id!!, testUserId)
       found shouldBe true

        mockMvc.patch("/api/reservations/event/{eventId}/user/{userId}", createdEvent.id, testUserId) {
            with(jwt().jwt {
                it.claim("userId", testUserId)
            })
            val requestBody = mapOf(
                "guests" to 0,
                "payload" to objectMapper.writeValueAsString(mapOf("extraInfo" to "string")),
                "status" to "ACCEPTED"
            )
            content = objectMapper.writeValueAsString(requestBody)
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.eventId", `is`(createdEvent.id.toString()))
                jsonPath("$.userId", `is`(testUserId.toString()))
                jsonPath("$.status", `is`("ACCEPTED"))
            }
    }

    afterSpec {
        reservationRepository.deleteAll()
    }
})

private fun ResultActionsDsl.toEvent(objectMapper: JsonMapper): Event =
    andReturn()
        .response
        .contentAsString
        .let { objectMapper.readValue(it, object : TypeReference<Event>() {}) }

private fun ResultActionsDsl.toReservation(
    objectMapper: JsonMapper,
    reservationMapper: ReservationMapper
): Reservation =
    andReturn()
        .response
        .contentAsString
        .let {
            objectMapper.readValue(it, object : TypeReference<ReservationResponse>() {})
                .let(reservationMapper::toBean)
        }