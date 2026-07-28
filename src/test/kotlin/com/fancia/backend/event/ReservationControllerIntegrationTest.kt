package com.fancia.backend.event

import com.fancia.backend.event.core.entity.Event
import com.fancia.backend.event.core.repository.EventOccurrenceRepository
import com.fancia.backend.event.core.repository.ReservationRepository
import com.fancia.backend.event.mapper.toEntity
import com.fancia.backend.shared.event.core.dto.EventResponse
import com.github.tomakehurst.wiremock.client.WireMock.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
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
    private val eventOccurrenceRepository: EventOccurrenceRepository,
    private val jsonMapper: JsonMapper,
    private val wiremock: WireMockContainer,
) : FunSpec({
    beforeSpec {
        configureFor(
            wiremock.host,
            wiremock.getMappedPort(8080),
        )
    }

    fun stubCreateTag(name: String): UUID {
        val tagId = UUID.randomUUID()
        stubFor(
            post(urlPathEqualTo("/api/tags"))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            jsonMapper.writeValueAsString(
                                mapOf(
                                    "content" to listOf(
                                        mapOf(
                                            "id" to tagId.toString(),
                                            "name" to name,
                                            "type" to "TOPIC",
                                        ),
                                    ),
                                    "totalElements" to 1,
                                    "totalPages" to 1,
                                    "size" to 1,
                                    "number" to 0,
                                ),
                            ),
                        ),
                ),
        )
        return tagId
    }

    fun firstOccurrenceId(eventId: UUID): UUID {
        return eventOccurrenceRepository.findByEventIdOrderByStartTimeAsc(eventId, PageRequest.of(0, 1))
            .content
            .first()
            .id!!
    }

    test("should create a new event") {
        val testUserId = UUID.randomUUID()
        val testInterestGroupId = UUID.randomUUID()
        stubCreateTag("good")
        val response = mockMvc
            .post("/api/events") {
                with(jwt().jwt {
                    it.claim("userId", testUserId)
                })
                val requestBody = mapOf(
                    "name" to "testEvent",
                    "description" to "string",
                    "startTime" to "2024-06-01T10:00:00",
                    "endTime" to "2024-06-01T12:00:00",
                    "interestGroups" to listOf(testInterestGroupId),
                    "tags" to listOf(mapOf("name" to "good", "type" to "TOPIC")),
                    "visibility" to "PUBLIC",
                    "links" to emptyList<Any>(),
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
        val createdEvent = response.toEvent(jsonMapper)
        val occurrenceId = firstOccurrenceId(createdEvent.id!!)
        mockMvc
            .post("/api/events/{eventId}/occurrences/{occurrenceId}/reservations", createdEvent.id, occurrenceId) {
                with(jwt().jwt {
                    it.claim("userId", testUserId)
                })
                val requestBody = mapOf(
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
                jsonPath("$.occurrenceId", `is`(occurrenceId.toString()))
                jsonPath("$.userId", `is`(testUserId.toString()))
                jsonPath("$.status", `is`("PENDING"))
            }
        val found = reservationRepository.existsByIdOccurrenceIdAndIdUserId(occurrenceId, testUserId)
        found shouldBe true

        mockMvc.patch(
            "/api/events/{eventId}/occurrences/{occurrenceId}/users/{userId}/reservations",
            createdEvent.id,
            occurrenceId,
            testUserId,
        ) {
            with(jwt().jwt {
                it.claim("userId", testUserId)
            })
            val requestBody = mapOf(
                "guests" to 0,
                "payload" to jsonMapper.writeValueAsString(mapOf("extraInfo" to "string")),
                "status" to "ACCEPTED",
            )
            content = jsonMapper.writeValueAsString(requestBody)
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.eventId", `is`(createdEvent.id.toString()))
                jsonPath("$.occurrenceId", `is`(occurrenceId.toString()))
                jsonPath("$.userId", `is`(testUserId.toString()))
                jsonPath("$.status", `is`("ACCEPTED"))
            }
        val testParticipantId = UUID.randomUUID()
        mockMvc.post("/api/events/{eventId}/occurrences/{occurrenceId}/reservations", createdEvent.id, occurrenceId) {
            with(jwt().jwt {
                it.claim("userId", testParticipantId)
            })
            val requestBody = mapOf(
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
                jsonPath("$.occurrenceId", `is`(occurrenceId.toString()))
                jsonPath("$.userId", `is`(testParticipantId.toString()))
                jsonPath("$.status", `is`("PENDING"))
            }

        mockMvc.get("/api/events/{eventId}/occurrences/{occurrenceId}/participants", createdEvent.id, occurrenceId) {
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

        mockMvc.patch(
            "/api/events/{eventId}/occurrences/{occurrenceId}/users/{userId}/reservations",
            createdEvent.id,
            occurrenceId,
            testParticipantId,
        ) {
            with(jwt().jwt {
                it.claim("userId", testUserId)
            })
            val requestBody = mapOf(
                "guests" to 0,
                "payload" to jsonMapper.writeValueAsString(mapOf("extraInfo" to "string")),
                "status" to "ACCEPTED",
            )
            content = jsonMapper.writeValueAsString(requestBody)
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }
            .andDo { print() }
            .andExpect {
                status { isOk() }
            }

        mockMvc.get("/api/events/{eventId}/occurrences/{occurrenceId}/participants", createdEvent.id, occurrenceId) {
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

    test("guest can withdraw and re-request reservation") {
        val hostUserId = UUID.randomUUID()
        val guestUserId = UUID.randomUUID()
        val testInterestGroupId = UUID.randomUUID()
        stubCreateTag("rerequest")
        val response = mockMvc
            .post("/api/events") {
                with(jwt().jwt {
                    it.claim("userId", hostUserId)
                })
                val requestBody = mapOf(
                    "name" to "rerequestEvent",
                    "description" to "string",
                    "startTime" to "2024-06-01T10:00:00",
                    "endTime" to "2024-06-01T12:00:00",
                    "interestGroups" to listOf(testInterestGroupId),
                    "tags" to listOf(mapOf("name" to "rerequest", "type" to "TOPIC")),
                    "visibility" to "PUBLIC",
                    "links" to emptyList<Any>(),
                    "approvalRequired" to true,
                )
                content = jsonMapper.writeValueAsString(requestBody)
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
            }
        val createdEvent = response.toEvent(jsonMapper)
        val occurrenceId = firstOccurrenceId(createdEvent.id!!)

        mockMvc
            .post("/api/events/{eventId}/occurrences/{occurrenceId}/reservations", createdEvent.id, occurrenceId) {
                with(jwt().jwt {
                    it.claim("userId", guestUserId)
                })
                content = jsonMapper.writeValueAsString(mapOf("guests" to 0, "payload" to ""))
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.status", `is`("PENDING"))
            }

        mockMvc.patch(
            "/api/events/{eventId}/occurrences/{occurrenceId}/users/{userId}/reservations",
            createdEvent.id,
            occurrenceId,
            guestUserId,
        ) {
            with(jwt().jwt {
                it.claim("userId", guestUserId)
            })
            content = jsonMapper.writeValueAsString(
                mapOf("guests" to 0, "payload" to "", "status" to "WITHDREW"),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.status", `is`("WITHDREW"))
            }

        mockMvc.patch(
            "/api/events/{eventId}/occurrences/{occurrenceId}/users/{userId}/reservations",
            createdEvent.id,
            occurrenceId,
            guestUserId,
        ) {
            with(jwt().jwt {
                it.claim("userId", guestUserId)
            })
            content = jsonMapper.writeValueAsString(
                mapOf("guests" to 0, "payload" to "", "status" to "PENDING"),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.status", `is`("PENDING"))
            }

        reservationRepository.findByIdOccurrenceIdAndIdUserId(occurrenceId, guestUserId)!!.status?.name shouldBe "PENDING"
    }

    afterSpec {
        reservationRepository.deleteAll()
    }
})

private fun ResultActionsDsl.toEvent(jsonMapper: JsonMapper): Event =
    andReturn()
        .response
        .contentAsString
        .let {
            jsonMapper.readValue(it, object : TypeReference<EventResponse>() {})
                .toEntity()
        }
