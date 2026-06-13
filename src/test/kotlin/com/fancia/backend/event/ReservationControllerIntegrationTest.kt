package com.fancia.backend.event

import com.fancia.backend.event.core.entity.Event
import com.fancia.backend.event.core.entity.Reservation
import com.fancia.backend.event.core.repository.ReservationRepository
import com.fancia.backend.event.mapper.EventMapper
import com.fancia.backend.event.mapper.ReservationMapper
import com.fancia.backend.shared.event.core.dto.EventResponse
import com.fancia.backend.shared.event.core.dto.ReservationResponse
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.configureFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.*
import org.testcontainers.junit.jupiter.Testcontainers
import org.wiremock.integrations.testcontainers.WireMockContainer
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
    private val jsonMapper: JsonMapper,
    private val eventMapper: EventMapper,
    private val wiremock: WireMockContainer,
) : FunSpec({
    beforeSpec {
        configureFor(
            wiremock.host,
            wiremock.getMappedPort(8080),
        )
    }

    test("should create a new event") {
        val testUserId = UUID.randomUUID()
        val testInterestGroupId = UUID.randomUUID()
        stubFor(
            get(urlPathEqualTo("/api/tags"))
                .withQueryParam("search", equalTo("good"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            jsonMapper.writeValueAsString(
                                mapOf(
                                    "content" to listOf(mapOf("name" to "good")),
                                    "totalElements" to 1,
                                    "totalPages" to 1,
                                    "size" to 20,
                                    "number" to 0,
                                )
                            )
                        )
                )
        )
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
                    "interestGroups" to listOf(testInterestGroupId),
                    "tags" to listOf("good"),
                    "visibility" to "PUBLIC",
                )
                content = jsonMapper.writeValueAsString(requestBody)
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.name", `is`("testEvent"))
                jsonPath("$.id", `is`(notNullValue()))
            }
        val createdEvent = response.toEvent(jsonMapper, eventMapper)
        val createReservationResponse = mockMvc
            .post("/api/events/{eventId}/reservations", createdEvent.id) {
                with(jwt().jwt {
                    it.claim("userId", testUserId)
                })
                val requestBody = mapOf(
                    "eventId" to createdEvent.id,
                    "guests" to 0,
                    "payload" to jsonMapper.writeValueAsString(mapOf("extraInfo" to "string")),
                )
                content = jsonMapper.writeValueAsString(requestBody)
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

        mockMvc.patch("/api/events/{eventId}/users/{userId}/reservations", createdEvent.id, testUserId) {
            with(jwt().jwt {
                it.claim("userId", testUserId)
            })
            val requestBody = mapOf(
                "guests" to 0,
                "payload" to jsonMapper.writeValueAsString(mapOf("extraInfo" to "string")),
                "status" to "ACCEPTED"
            )
            content = jsonMapper.writeValueAsString(requestBody)
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
        val testParticipantId = UUID.randomUUID()
        mockMvc.post("/api/events/{eventId}/reservations", createdEvent.id) {
            with(jwt().jwt {
                it.claim("userId", testParticipantId)
            })
            val requestBody = mapOf(
                "eventId" to createdEvent.id,
                "guests" to 0,
                "payload" to jsonMapper.writeValueAsString(mapOf("extraInfo" to "string")),
            )
            content = jsonMapper.writeValueAsString(requestBody)
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.eventId", `is`(createdEvent.id.toString()))
                jsonPath("$.userId", `is`(testParticipantId.toString()))
                jsonPath("$.status", `is`("PENDING"))
            }

        mockMvc.get("/api/events/{eventId}/participants", createdEvent.id) {
            with(jwt().jwt {
                it.claim("userId", testUserId)
            })
            accept = APPLICATION_JSON
        }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.totalElements", `is`(1))
            }

        mockMvc.patch("/api/events/{eventId}/users/{userId}/reservations", createdEvent.id, testParticipantId) {
            with(jwt().jwt {
                it.claim("userId", testUserId)
            })
            val requestBody = mapOf(
                "guests" to 0,
                "payload" to jsonMapper.writeValueAsString(mapOf("extraInfo" to "string")),
                "status" to "ACCEPTED"
            )
            content = jsonMapper.writeValueAsString(requestBody)
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }
            .andDo { print() }
            .andExpect {
                status { isOk() }
            }

        mockMvc.get("/api/events/{eventId}/participants", createdEvent.id) {
            with(jwt().jwt {
                it.claim("userId", testUserId)
            })
            accept = APPLICATION_JSON
        }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.totalElements", `is`(2))
            }
    }

    afterSpec {
        reservationRepository.deleteAll()
    }
})

private fun ResultActionsDsl.toEvent(jsonMapper: JsonMapper, eventMapper: EventMapper): Event =
    andReturn()
        .response
        .contentAsString
        .let {
            jsonMapper.readValue(it, object : TypeReference<EventResponse>() {}).let(
                eventMapper::toBean
            )
        }

private fun ResultActionsDsl.toReservation(
    jsonMapper: JsonMapper,
    reservationMapper: ReservationMapper
): Reservation =
    andReturn()
        .response
        .contentAsString
        .let {
            jsonMapper.readValue(it, object : TypeReference<ReservationResponse>() {})
                .let(reservationMapper::toBean)
        }