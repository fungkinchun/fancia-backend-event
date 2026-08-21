package com.fancia.backend.event

import com.fancia.backend.event.core.repository.EventOccurrenceRepository
import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.event.core.repository.EventTicketTierRepository
import com.fancia.backend.event.core.repository.ReservationRepository
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.configureFor
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.reset
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Testcontainers
import org.wiremock.integrations.testcontainers.WireMockContainer
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@SpringBootTest(classes = [EventApplication::class])
@AutoConfigureMockMvc
@Testcontainers
@Import(TestConfig::class)
class EventTicketTierControllerIntegrationTest(
    private val mockMvc: MockMvc,
    private val eventRepository: EventRepository,
    private val eventOccurrenceRepository: EventOccurrenceRepository,
    private val eventTicketTierRepository: EventTicketTierRepository,
    private val reservationRepository: ReservationRepository,
    private val jsonMapper: JsonMapper,
    private val wiremock: WireMockContainer,
) : FunSpec({
    beforeSpec {
        configureFor(wiremock.host, wiremock.getMappedPort(8080))
    }

    beforeEach {
        reset()
        reservationRepository.deleteAll()
        eventTicketTierRepository.deleteAll()
        eventRepository.deleteAll()
    }

    fun jwtFor(userId: UUID) = jwt().jwt { it.claim("userId", userId) }

    fun stubCreateTag(name: String) {
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
                                            "id" to UUID.randomUUID().toString(),
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
    }

    fun stubPayoutReady(userId: UUID, ready: Boolean) {
        stubFor(
            get(urlPathEqualTo("/internal/connect/accounts/$userId"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            jsonMapper.writeValueAsString(
                                mapOf(
                                    "userId" to userId.toString(),
                                    "provider" to "stripe",
                                    "providerId" to if (ready) "acct_test" else null,
                                    "payoutsReady" to ready,
                                    "chargesEnabled" to ready,
                                    "payoutsEnabled" to ready,
                                    "detailsSubmitted" to ready,
                                    "defaultCurrency" to "gbp",
                                ),
                            ),
                        ),
                ),
        )
    }

    fun createEvent(hostId: UUID, approvalRequired: Boolean = true): UUID {
        stubCreateTag("tickets")
        val body = mockMvc.post("/api/events") {
            with(jwtFor(hostId))
            content = jsonMapper.writeValueAsString(
                mapOf(
                    "name" to "Ticketed Event",
                    "description" to "Event for ticket tests",
                    "startTime" to "2030-06-01T10:00:00",
                    "endTime" to "2030-06-01T12:00:00",
                    "interestGroups" to listOf(UUID.randomUUID()),
                    "tags" to listOf(mapOf("name" to "tickets", "type" to "TOPIC")),
                    "visibility" to "PUBLIC",
                    "links" to emptyList<Any>(),
                    "approvalRequired" to approvalRequired,
                ),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.id", `is`(notNullValue()))
        }.andReturn().response.contentAsString
        return UUID.fromString(jsonMapper.readTree(body).get("id").asText())
    }

    fun firstOccurrenceId(eventId: UUID): UUID =
        eventOccurrenceRepository.findByEventIdOrderByStartTimeAsc(eventId, PageRequest.of(0, 1))
            .content
            .first()
            .id!!

    test("should create free ticket tier without payout check") {
        val hostId = UUID.randomUUID()
        val eventId = createEvent(hostId)

        mockMvc.post("/api/events/{eventId}/ticket-tiers", eventId) {
            with(jwtFor(hostId))
            content = jsonMapper.writeValueAsString(
                mapOf(
                    "name" to "General",
                    "priceMinor" to 0,
                    "currency" to "gbp",
                ),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.name", `is`("General"))
            jsonPath("$.priceMinor", `is`(0))
            jsonPath("$.id", `is`(notNullValue()))
        }

        mockMvc.get("/api/events/{eventId}/ticket-tiers", eventId) {
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()", `is`(1))
        }
    }

    test("should reject paid ticket tier when host payouts are not ready") {
        val hostId = UUID.randomUUID()
        val eventId = createEvent(hostId)
        stubPayoutReady(hostId, ready = false)

        mockMvc.post("/api/events/{eventId}/ticket-tiers", eventId) {
            with(jwtFor(hostId))
            content = jsonMapper.writeValueAsString(
                mapOf(
                    "name" to "VIP",
                    "priceMinor" to 1500,
                    "currency" to "gbp",
                ),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errorCode", `is`("EVENT_HOST_PAYOUT_NOT_READY"))
        }
    }

    test("free tier reservation goes to paid then host can accept") {
        val hostId = UUID.randomUUID()
        val guestId = UUID.randomUUID()
        val eventId = createEvent(hostId, approvalRequired = true)
        val occurrenceId = firstOccurrenceId(eventId)

        val tierBody = mockMvc.post("/api/events/{eventId}/ticket-tiers", eventId) {
            with(jwtFor(hostId))
            content = jsonMapper.writeValueAsString(
                mapOf("name" to "Free", "priceMinor" to 0, "currency" to "gbp"),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val tierId = UUID.fromString(jsonMapper.readTree(tierBody).get("id").asText())

        mockMvc.post(
            "/api/events/{eventId}/occurrences/{occurrenceId}/reservations",
            eventId,
            occurrenceId,
        ) {
            with(jwtFor(guestId))
            content = jsonMapper.writeValueAsString(
                mapOf(
                    "guests" to 0,
                    "payload" to "",
                    "tierId" to tierId.toString(),
                ),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.status", `is`("PAID"))
            jsonPath("$.tierId", `is`(tierId.toString()))
            jsonPath("$.priceMinor", `is`(0))
        }

        mockMvc.patch(
            "/api/events/{eventId}/occurrences/{occurrenceId}/users/{userId}/reservations",
            eventId,
            occurrenceId,
            guestId,
        ) {
            with(jwtFor(hostId))
            content = jsonMapper.writeValueAsString(
                mapOf("guests" to 0, "payload" to "", "status" to "ACCEPTED"),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.status", `is`("ACCEPTED"))
        }
    }

    test("paid tier stays pending until internal confirm paid") {
        val hostId = UUID.randomUUID()
        val guestId = UUID.randomUUID()
        val eventId = createEvent(hostId, approvalRequired = true)
        val occurrenceId = firstOccurrenceId(eventId)
        stubPayoutReady(hostId, ready = true)

        val tierBody = mockMvc.post("/api/events/{eventId}/ticket-tiers", eventId) {
            with(jwtFor(hostId))
            content = jsonMapper.writeValueAsString(
                mapOf(
                    "name" to "Standard",
                    "priceMinor" to 2000,
                    "currency" to "gbp",
                    "capacityPerOccurrence" to 10,
                ),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.priceMinor", `is`(2000))
        }.andReturn().response.contentAsString
        val tierId = UUID.fromString(jsonMapper.readTree(tierBody).get("id").asText())

        mockMvc.post(
            "/api/events/{eventId}/occurrences/{occurrenceId}/reservations",
            eventId,
            occurrenceId,
        ) {
            with(jwtFor(guestId))
            content = jsonMapper.writeValueAsString(
                mapOf(
                    "guests" to 0,
                    "payload" to "",
                    "tierId" to tierId.toString(),
                ),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.status", `is`("PENDING"))
            jsonPath("$.priceMinor", `is`(2000))
        }

        mockMvc.get(
            "/internal/events/{eventId}/occurrences/{occurrenceId}/reservations/{userId}/checkout",
            eventId,
            occurrenceId,
            guestId,
        ) {
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.priceMinor", `is`(2000))
            jsonPath("$.reservationStatus", `is`("PENDING"))
            jsonPath("$.tierId", `is`(tierId.toString()))
        }

        mockMvc.post(
            "/internal/events/{eventId}/occurrences/{occurrenceId}/reservations/{userId}/paid",
            eventId,
            occurrenceId,
            guestId,
        ) {
            content = jsonMapper.writeValueAsString(mapOf("checkoutSessionId" to "cs_test_ticket"))
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.status", `is`("PAID"))
        }

        reservationRepository.findByIdOccurrenceIdAndIdUserId(occurrenceId, guestId)!!.status?.name shouldBe "PAID"
    }

    test("pending paid reservation can start checkout via payment proxy") {
        val hostId = UUID.randomUUID()
        val guestId = UUID.randomUUID()
        val eventId = createEvent(hostId, approvalRequired = true)
        val occurrenceId = firstOccurrenceId(eventId)
        stubPayoutReady(hostId, ready = true)

        val tierBody = mockMvc.post("/api/events/{eventId}/ticket-tiers", eventId) {
            with(jwtFor(hostId))
            content = jsonMapper.writeValueAsString(
                mapOf("name" to "Standard", "priceMinor" to 2000, "currency" to "gbp"),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val tierId = UUID.fromString(jsonMapper.readTree(tierBody).get("id").asText())

        mockMvc.post(
            "/api/events/{eventId}/occurrences/{occurrenceId}/reservations",
            eventId,
            occurrenceId,
        ) {
            with(jwtFor(guestId))
            content = jsonMapper.writeValueAsString(
                mapOf("guests" to 0, "payload" to "", "tierId" to tierId.toString()),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.status", `is`("PENDING"))
        }

        stubFor(
            post(urlPathEqualTo("/internal/checkout/sessions"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            jsonMapper.writeValueAsString(
                                mapOf(
                                    "url" to "https://checkout.stripe.com/ticket",
                                    "sessionId" to "cs_test_ticket_proxy",
                                    "provider" to "STRIPE",
                                    "amountMinor" to 2000,
                                    "applicationFeeMinor" to 100,
                                    "currency" to "gbp",
                                ),
                            ),
                        ),
                ),
        )

        mockMvc.post(
            "/api/events/{eventId}/occurrences/{occurrenceId}/reservations/checkout",
            eventId,
            occurrenceId,
        ) {
            with(jwtFor(guestId))
            content = jsonMapper.writeValueAsString(
                mapOf(
                    "successUrl" to "https://fancia.co.uk/success",
                    "cancelUrl" to "https://fancia.co.uk/cancel",
                ),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.url", `is`("https://checkout.stripe.com/ticket"))
            jsonPath("$.sessionId", `is`("cs_test_ticket_proxy"))
        }
    }

    afterSpec {
        reservationRepository.deleteAll()
        eventTicketTierRepository.deleteAll()
        eventRepository.deleteAll()
    }
})
