package com.fancia.backend.event

import com.fancia.backend.event.core.entity.Event
import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.event.mapper.toEntity
import com.fancia.backend.shared.event.core.dto.EventResponse
import com.fancia.backend.shared.event.core.enums.RecurrenceFrequency
import com.github.tomakehurst.wiremock.client.WireMock.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.data.domain.Page
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
    val otherUserId = UUID.randomUUID()
    val tagRegistry = mutableListOf<Pair<UUID, String>>()

    beforeSpec {
        configureFor(
            wiremock.host,
            wiremock.getMappedPort(8080)
        )
    }

    fun refreshTagLookupStubs() {
        val tags = tagRegistry.distinct()
        stubFor(
            get(urlPathEqualTo("/api/tags/ids"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            jsonMapper.writeValueAsString(
                                tags.map { (id, name) ->
                                    mapOf(
                                        "id" to id.toString(),
                                        "name" to name,
                                        "type" to "TOPIC",
                                    )
                                },
                            ),
                        ),
                ),
        )
        stubFor(
            get(urlPathEqualTo("/api/tags"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            jsonMapper.writeValueAsString(
                                mapOf(
                                    "content" to emptyList<Any>(),
                                    "totalElements" to 0,
                                    "totalPages" to 0,
                                    "size" to 0,
                                    "number" to 0,
                                ),
                            ),
                        ),
                ),
        )
    }

    fun stubTag(tagId: UUID, name: String) {
        tagRegistry.add(tagId to name)
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
        refreshTagLookupStubs()
    }

    fun stubCreateTag(name: String): UUID {
        val tagId = UUID.randomUUID()
        stubTag(tagId, name)
        return tagId
    }

    fun jwtFor(userId: UUID) = jwt().jwt { it.claim("userId", userId) }
    fun createFutureEvent(
        createdBy: UUID,
        name: String,
        startTime: String,
        endTime: String,
        tagName: String,
        tagId: UUID,
        visibility: String = "PUBLIC",
        location: Map<String, Any?>? = null,
    ): EventResponse {
        stubTag(tagId, tagName)
        val requestBody = buildMap {
            put("name", name)
            put("description", "Personalized list test event")
            put("startTime", startTime)
            put("endTime", endTime)
            put("interestGroups", listOf(testInterestGroupId))
            put("tags", listOf(mapOf("name" to tagName, "type" to "TOPIC")))
            put("visibility", visibility)
            put("links", emptyList<Any>())
            location?.let { put("location", it) }
        }
        return mockMvc
            .post("/api/events") {
                with(jwtFor(createdBy))
                content = jsonMapper.writeValueAsString(requestBody)
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect { status { isOk() } }
            .toEventResponse(jsonMapper)
    }

    fun preparePersonalizedTest() {
        reset()
        tagRegistry.clear()
        eventRepository.deleteAll()
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
                    "startTime" to "2030-06-01T10:00:00",
                    "endTime" to "2030-06-01T12:00:00",
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
                        "startTime" to "2030-06-02T10:00:00",
                        "endTime" to "2030-06-02T11:00:00",
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

    test("should match events by user interests when match=true") {
        preparePersonalizedTest()
        val hikingTagId = UUID.randomUUID()
        val cookingTagId = UUID.randomUUID()
        val matchedEvent = createFutureEvent(
            createdBy = otherUserId,
            name = "Hiking Meetup",
            startTime = "2030-06-01T10:00:00",
            endTime = "2030-06-01T12:00:00",
            tagName = "hiking",
            tagId = hikingTagId,
        )
        createFutureEvent(
            createdBy = otherUserId,
            name = "Cooking Class",
            startTime = "2030-06-02T10:00:00",
            endTime = "2030-06-02T12:00:00",
            tagName = "cooking",
            tagId = cookingTagId,
        )
        val response = mockMvc
            .get("/api/events?match=true&tagIds=$hikingTagId") {
                with(jwtFor(testUserId))
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.content.length()", `is`(1))
                jsonPath("$.content[0].id", `is`(matchedEvent.id.toString()))
                jsonPath("$.content[0].name", `is`("Hiking Meetup"))
            }
            .toEventPage(jsonMapper)

        response.content.single().id shouldBe matchedEvent.id
    }

    test("should exclude private events when match=true") {
        preparePersonalizedTest()
        val secretTagId = UUID.randomUUID()

        createFutureEvent(
            createdBy = otherUserId,
            name = "Secret Event",
            startTime = "2030-06-01T10:00:00",
            endTime = "2030-06-01T12:00:00",
            tagName = "secret",
            tagId = secretTagId,
            visibility = "PRIVATE",
        )

        mockMvc
            .get("/api/events?match=true&tagIds=$secretTagId") {
                with(jwtFor(testUserId))
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.content.length()", `is`(0))
            }
    }

    test("should match nearby events by location label when schedule=true") {
        preparePersonalizedTest()
        val socialTagId = UUID.randomUUID()
        val londonEvent = createFutureEvent(
            createdBy = otherUserId,
            name = "London Social",
            startTime = "2030-06-01T14:00:00",
            endTime = "2030-06-01T16:00:00",
            tagName = "social",
            tagId = socialTagId,
            location = mapOf(
                "kind" to "ADDRESS",
                "city" to "London",
                "country" to "UK",
            ),
        )
        createFutureEvent(
            createdBy = otherUserId,
            name = "Paris Social",
            startTime = "2030-06-01T14:00:00",
            endTime = "2030-06-01T16:00:00",
            tagName = "social",
            tagId = socialTagId,
            location = mapOf(
                "kind" to "ADDRESS",
                "city" to "Paris",
                "country" to "France",
            ),
        )
        val response = mockMvc
            .get("/api/events?schedule=true&locationLabel=London") {
                with(jwtFor(testUserId))
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.content.length()", `is`(1))
                jsonPath("$.content[0].id", `is`(londonEvent.id.toString()))
            }
            .toEventPage(jsonMapper)

        response.content.map { it.id } shouldContain londonEvent.id
    }

    test("should create recurring event as a single row") {
        preparePersonalizedTest()
        stubCreateTag("yoga")
        val createResponse = mockMvc
            .post("/api/events") {
                with(jwtFor(testUserId))
                content = jsonMapper.writeValueAsString(
                    mapOf(
                        "name" to "Weekly Yoga",
                        "description" to "Recurring yoga class",
                        "startTime" to "2030-06-03T10:00:00",
                        "endTime" to "2030-06-03T11:00:00",
                        "interestGroups" to listOf(testInterestGroupId),
                        "tags" to listOf(mapOf("name" to "yoga", "type" to "TOPIC")),
                        "visibility" to "PUBLIC",
                        "links" to emptyList<Any>(),
                        "recurrence" to mapOf(
                            "frequency" to "WEEKLY",
                            "daysOfWeek" to listOf("MONDAY", "FRIDAY"),
                        ),
                    ),
                )
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.name", `is`("Weekly Yoga"))
                jsonPath("$.recurrence.frequency", `is`("WEEKLY"))
            }
            .toEventResponse(jsonMapper)

        eventRepository.findAll().size shouldBe 1
        eventRepository.findByIdOrNull(createResponse.id!!)?.recurrenceFrequency shouldBe RecurrenceFrequency.WEEKLY
    }

    test("should exclude schedule conflicts when schedule=true") {
        preparePersonalizedTest()
        val busyTagId = UUID.randomUUID()
        val conflictTagId = UUID.randomUUID()
        val freeTagId = UUID.randomUUID()

        createFutureEvent(
            createdBy = testUserId,
            name = "Busy Event",
            startTime = "2030-06-01T10:00:00",
            endTime = "2030-06-01T12:00:00",
            tagName = "busy",
            tagId = busyTagId,
            location = mapOf(
                "kind" to "ADDRESS",
                "city" to "London",
                "country" to "UK",
            ),
        )
        val conflictingEvent = createFutureEvent(
            createdBy = otherUserId,
            name = "Conflicting Event",
            startTime = "2030-06-01T11:00:00",
            endTime = "2030-06-01T13:00:00",
            tagName = "conflict",
            tagId = conflictTagId,
            location = mapOf(
                "kind" to "ADDRESS",
                "city" to "London",
                "country" to "UK",
            ),
        )
        val freeEvent = createFutureEvent(
            createdBy = otherUserId,
            name = "Free Event",
            startTime = "2030-06-01T14:00:00",
            endTime = "2030-06-01T16:00:00",
            tagName = "free",
            tagId = freeTagId,
            location = mapOf(
                "kind" to "ADDRESS",
                "city" to "London",
                "country" to "UK",
            ),
        )
        val response = mockMvc
            .get("/api/events?schedule=true&locationLabel=London") {
                with(jwtFor(testUserId))
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.content.length()", `is`(1))
                jsonPath("$.content[0].id", `is`(freeEvent.id.toString()))
            }
            .toEventPage(jsonMapper)

        response.content.map { it.id } shouldContain freeEvent.id
        response.content.map { it.id } shouldNotContain conflictingEvent.id
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

private fun ResultActionsDsl.toEventResponse(jsonMapper: JsonMapper): EventResponse =
    andReturn()
        .response
        .contentAsString
        .let { jsonMapper.readValue(it, object : TypeReference<EventResponse>() {}) }

private fun ResultActionsDsl.toEventPage(jsonMapper: JsonMapper): Page<EventResponse> =
    andReturn()
        .response
        .contentAsString
        .let { jsonMapper.readValue(it, object : TypeReference<Page<EventResponse>>() {}) }