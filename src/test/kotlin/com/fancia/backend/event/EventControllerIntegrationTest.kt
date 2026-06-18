package com.fancia.backend.event

import com.fancia.backend.event.core.entity.Event
import com.fancia.backend.event.core.repository.EventRepository
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
) : FunSpec({
    val testInterestGroupId = UUID.randomUUID()
    val testUserId = UUID.randomUUID()

    beforeSpec {
        configureFor(
            wiremock.host,
            wiremock.getMappedPort(8080)
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

    test("should create a new event") {

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
                jsonPath("$.interestGroups[0]", `is`(testInterestGroupId.toString()))
            }
        val createdEvent = response.toEvent(jsonMapper)
        val found = eventRepository.findByIdOrNull(createdEvent.id!!)
        found?.id shouldBe createdEvent.id
    }

    test("should list events") {
        val event = eventRepository.findAll().first { it.name == "testEvent" }
        val tagId = event.tags.first()
        mockMvc
            .get("/api/events?tagIds=$tagId&page=0&size=3") {
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.totalElements", `is`(1))
                jsonPath("$.content[0].name", `is`("testEvent"))
                jsonPath("$.content[0].tags[0]", `is`(event.tags.first().toString()))
                jsonPath("$.content[0].visibility", `is`("PUBLIC"))
            }
    }

    test("should not list events because of wrong tag") {
        mockMvc
            .get("/api/events?tagIds=${UUID.randomUUID()}&page=0&size=3") {
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.totalElements", `is`(0))
            }
    }

    test("should get event by id") {
        val eventId = eventRepository.findAll().first { it.name == "testEvent" }.id!!
        mockMvc
            .get("/api/events/$eventId") {
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.id", `is`(eventId.toString()))
                jsonPath("$.name", `is`("testEvent"))
            }
    }

    test("should not list private events but allow direct access by id") {

        val secretTagId = stubCreateTag("secret")
        val createResponse = mockMvc
            .post("/api/events") {
                with(jwt().jwt { it.claim("userId", testUserId) })
                content = jsonMapper.writeValueAsString(
                    mapOf(
                        "name" to "Secret Event",
                        "description" to "Invite only",
                        "startTime" to "2024-06-02T10:00:00",
                        "endTime" to "2024-06-02T11:00:00",
                        "interestGroups" to listOf(testInterestGroupId),
                        "tags" to listOf(mapOf("name" to "secret", "type" to "TOPIC")),
                        "visibility" to "PRIVATE",
                        "links" to emptyList<Any>(),
                    )
                )
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.visibility", `is`("PRIVATE"))
            }
            .andReturn()
            .response
            .contentAsString
        val createdPrivateEventId = jsonMapper.readTree(createResponse).get("id").asText()

        mockMvc
            .get("/api/events?tagIds=$secretTagId&page=0&size=10") {
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.totalElements", `is`(0))
            }

        mockMvc
            .get("/api/events/$createdPrivateEventId") {
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.name", `is`("Secret Event"))
                jsonPath("$.visibility", `is`("PRIVATE"))
            }
    }

    test("should list events filtered by interest group id") {
        mockMvc
            .get("/api/events?interestGroup=$testInterestGroupId&page=0&size=3") {
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.totalElements", `is`(1))
                jsonPath("$.content[0].name", `is`("testEvent"))
            }
    }

    test("should not list events for non-matching interest group id") {
        val otherInterestGroupId = UUID.randomUUID()
        mockMvc
            .get("/api/events?interestGroup=$otherInterestGroupId&page=0&size=3") {
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.totalElements", `is`(0))
            }
    }

    test("should list events filtered by interest group id and tags") {
        val event = eventRepository.findAll().first { it.name == "testEvent" }
        val tagId = event.tags.first()
        mockMvc
            .get("/api/events?interestGroup=$testInterestGroupId&tagIds=$tagId&page=0&size=3") {
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.totalElements", `is`(1))
                jsonPath("$.content[0].name", `is`("testEvent"))
            }
    }

    test("should not list events when interest group id and tags do not match") {
        mockMvc
            .get("/api/events?interestGroup=$testInterestGroupId&tagIds=${UUID.randomUUID()}&page=0&size=3") {
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

private fun ResultActionsDsl.toEvent(jsonMapper: JsonMapper): Event =
    andReturn()
        .response
        .contentAsString
        .let {
            jsonMapper.readValue(it, object : TypeReference<EventResponse>() {})
                .toEntity()
        }