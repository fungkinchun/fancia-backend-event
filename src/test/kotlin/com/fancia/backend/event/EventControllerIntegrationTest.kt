package com.fancia.backend.event

import com.fancia.backend.event.core.entity.Event
import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.event.mapper.EventMapper
import com.fancia.backend.shared.event.core.dto.EventResponse
import com.github.tomakehurst.wiremock.client.WireMock.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Testcontainers
import org.wiremock.integrations.testcontainers.WireMockContainer
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.util.*

@SpringBootTest(classes = [EventApplication::class])
@AutoConfigureMockMvc
@Testcontainers
@Import(TestConfig::class)
class EventControllerIntegrationTest(
    private val mockMvc: MockMvc,
    private val wiremock: WireMockContainer,
    private val eventRepository: EventRepository,
    private val jsonMapper: JsonMapper,
    private val eventMapper: EventMapper
) : FunSpec({
    beforeSpec {
        configureFor(
            wiremock.host,
            wiremock.getMappedPort(8080)
        )
    }
    test("should create a new event") {
        val testUserId = UUID.randomUUID()
        val testInterestGroupId = UUID.randomUUID()
        val mockResponse = mapOf(
            "content" to listOf(
                mapOf(
                    "name" to "good"
                )
            ),
            "totalElements" to 1,
            "totalPages" to 1,
            "size" to 20,
            "number" to 0
        )
        stubFor(
            get(urlPathEqualTo("/api/tags"))
                .withQueryParam("search", equalTo("good"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            jsonMapper.writeValueAsString(
                                mockResponse
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
                    "interestGroupId" to testInterestGroupId,
                    "tags" to listOf("good")
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
        val found = eventRepository.findByIdOrNull(createdEvent.id!!)
        createdEvent shouldBe found
    }

    test("should list events") {
        mockMvc
            .get("/api/events?tags=good&page=0&size=3") {
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.totalElements", `is`(1))
                jsonPath("$.content[0].name", `is`("testEvent"))
                jsonPath("$.content[0].tags[0]", `is`("good"))
            }
    }

    test("should not list events because of wrong tag") {
        mockMvc
            .get("/api/events?tags=bad&page=0&size=3") {
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.totalElements", `is`(0))
            }
    }

    afterSpec {
        eventRepository.deleteAll()
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