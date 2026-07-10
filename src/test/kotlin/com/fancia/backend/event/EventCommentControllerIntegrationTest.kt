package com.fancia.backend.event

import com.fancia.backend.event.core.repository.EventOccurrenceRepository
import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.shared.common.comment.core.dto.CommentResponse
import com.github.tomakehurst.wiremock.client.WireMock.*
import io.kotest.core.spec.style.FunSpec
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Testcontainers
import org.wiremock.integrations.testcontainers.WireMockContainer
import tools.jackson.databind.json.JsonMapper
import java.util.*

@SpringBootTest(classes = [EventApplication::class])
@AutoConfigureMockMvc
@Testcontainers
@Import(TestConfig::class)
class EventCommentControllerIntegrationTest(
    private val mockMvc: MockMvc,
    private val eventRepository: EventRepository,
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

    beforeEach {
        reset()
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

    fun createEventViaApi(userId: UUID): UUID {
        val testInterestGroupId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val tagName = "test"
        stubCreateTag(tagName)
        val responseBody = mockMvc
            .post("/api/events") {
                with(jwt().jwt { it.claim("userId", userId) })
                content = jsonMapper.writeValueAsString(
                    mapOf(
                        "name" to "Comment Test Event",
                        "description" to "Event for comment integration tests",
                        "startTime" to "2024-06-01T10:00:00",
                        "endTime" to "2024-06-01T12:00:00",
                        "interestGroups" to listOf(testInterestGroupId),
                        "tags" to listOf(mapOf("name" to tagName, "type" to "TOPIC")),
                        "visibility" to "PUBLIC",
                        "links" to emptyList<Any>(),
                    ),
                )
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.id", `is`(notNullValue()))
            }
            .andReturn()
            .response
            .contentAsString

        return UUID.fromString(jsonMapper.readTree(responseBody).get("id").asText())
    }

    fun firstOccurrenceId(eventId: UUID): UUID {
        return eventOccurrenceRepository.findByEventIdOrderByStartTimeAsc(eventId, PageRequest.of(0, 1))
            .content
            .first()
            .id!!
    }

    test("should forward occurrence wall comment creation to common-internal") {
        val userId = UUID.randomUUID()
        val eventId = createEventViaApi(userId)
        val occurrenceId = firstOccurrenceId(eventId)
        val commentId = UUID.randomUUID()
        val commonResponse = CommentResponse(
            id = commentId,
            targetId = occurrenceId,
            resourceId = occurrenceId,
            authorUserId = userId,
            body = "Great session",
            createdAt = null,
        )
        stubFor(
            post(urlPathEqualTo("/internal/comments"))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(commonResponse)),
                ),
        )

        mockMvc
            .post("/api/events/$eventId/occurrences/$occurrenceId/comments") {
                with(jwt().jwt { it.claim("userId", userId) })
                content = jsonMapper.writeValueAsString(
                    mapOf(
                        "targetId" to occurrenceId.toString(),
                        "resourceId" to occurrenceId.toString(),
                        "body" to "Great session",
                    ),
                )
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isCreated() }
                jsonPath("$.id", `is`(commentId.toString()))
                jsonPath("$.targetId", `is`(occurrenceId.toString()))
                jsonPath("$.resourceId", `is`(occurrenceId.toString()))
            }

        verify(
            postRequestedFor(urlPathEqualTo("/internal/comments"))
                .withRequestBody(matchingJsonPath("$.targetId", equalTo(occurrenceId.toString())))
                .withRequestBody(matchingJsonPath("$.resourceId", equalTo(occurrenceId.toString())))
                .withRequestBody(matchingJsonPath("$.body", equalTo("Great session"))),
        )
    }

    test("should forward post comment creation to common-internal") {
        val userId = UUID.randomUUID()
        val eventId = createEventViaApi(userId)
        val occurrenceId = firstOccurrenceId(eventId)
        val postId = UUID.randomUUID()
        val commentId = UUID.randomUUID()
        val commonResponse = CommentResponse(
            id = commentId,
            targetId = postId,
            resourceId = occurrenceId,
            authorUserId = userId,
            body = "Nice post",
            createdAt = null,
        )
        stubFor(
            post(urlPathEqualTo("/internal/comments"))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(commonResponse)),
                ),
        )

        mockMvc
            .post("/api/events/$eventId/occurrences/$occurrenceId/comments") {
                with(jwt().jwt { it.claim("userId", userId) })
                content = jsonMapper.writeValueAsString(
                    mapOf(
                        "targetId" to postId.toString(),
                        "resourceId" to occurrenceId.toString(),
                        "body" to "Nice post",
                    ),
                )
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isCreated() }
                jsonPath("$.id", `is`(commentId.toString()))
                jsonPath("$.resourceId", `is`(occurrenceId.toString()))
            }

        verify(
            postRequestedFor(urlPathEqualTo("/internal/comments"))
                .withRequestBody(matchingJsonPath("$.targetId", equalTo(postId.toString())))
                .withRequestBody(matchingJsonPath("$.resourceId", equalTo(occurrenceId.toString())))
                .withRequestBody(matchingJsonPath("$.body", equalTo("Nice post"))),
        )
    }

    test("should return bad request when event does not exist") {
        val missingEventId = UUID.randomUUID()
        val missingOccurrenceId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        mockMvc
            .post("/api/events/$missingEventId/occurrences/$missingOccurrenceId/comments") {
                with(jwt().jwt { it.claim("userId", userId) })
                content = jsonMapper.writeValueAsString(
                    mapOf(
                        "targetId" to missingOccurrenceId.toString(),
                        "resourceId" to missingOccurrenceId.toString(),
                        "body" to "Should fail",
                    ),
                )
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect { status { isBadRequest() } }

        verify(0, postRequestedFor(urlPathEqualTo("/internal/comments")))
    }

    test("should list occurrence wall comments with targetId defaulting to occurrenceId") {
        val userId = UUID.randomUUID()
        val eventId = createEventViaApi(userId)
        val occurrenceId = firstOccurrenceId(eventId)
        val pageResponse = mapOf(
            "content" to emptyList<Any>(),
            "totalElements" to 0,
            "totalPages" to 0,
            "size" to 20,
            "number" to 0,
        )
        stubFor(
            get(urlPathEqualTo("/internal/comments"))
                .withQueryParam("targetId", equalTo(occurrenceId.toString()))
                .withQueryParam("resourceId", equalTo(occurrenceId.toString()))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(pageResponse)),
                ),
        )

        mockMvc
            .get("/api/events/$eventId/occurrences/$occurrenceId/comments") {
                accept = APPLICATION_JSON
            }
            .andExpect { status { isOk() } }

        verify(
            getRequestedFor(urlPathEqualTo("/internal/comments"))
                .withQueryParam("targetId", equalTo(occurrenceId.toString()))
                .withQueryParam("resourceId", equalTo(occurrenceId.toString())),
        )
    }

    test("should list post comments when targetId is post id") {
        val userId = UUID.randomUUID()
        val eventId = createEventViaApi(userId)
        val occurrenceId = firstOccurrenceId(eventId)
        val postId = UUID.randomUUID()
        val pageResponse = mapOf(
            "content" to emptyList<Any>(),
            "totalElements" to 0,
            "totalPages" to 0,
            "size" to 20,
            "number" to 0,
        )
        stubFor(
            get(urlPathEqualTo("/internal/comments"))
                .withQueryParam("targetId", equalTo(postId.toString()))
                .withQueryParam("resourceId", equalTo(occurrenceId.toString()))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(pageResponse)),
                ),
        )

        mockMvc
            .get("/api/events/$eventId/occurrences/$occurrenceId/comments") {
                param("targetId", postId.toString())
                accept = APPLICATION_JSON
            }
            .andExpect { status { isOk() } }

        verify(
            getRequestedFor(urlPathEqualTo("/internal/comments"))
                .withQueryParam("targetId", equalTo(postId.toString()))
                .withQueryParam("resourceId", equalTo(occurrenceId.toString())),
        )
    }

    test("should list replies when targetId is parent comment id") {
        val userId = UUID.randomUUID()
        val eventId = createEventViaApi(userId)
        val occurrenceId = firstOccurrenceId(eventId)
        val parentCommentId = UUID.randomUUID()
        val pageResponse = mapOf(
            "content" to emptyList<Any>(),
            "totalElements" to 0,
            "totalPages" to 0,
            "size" to 20,
            "number" to 0,
        )
        stubFor(
            get(urlPathEqualTo("/internal/comments"))
                .withQueryParam("targetId", equalTo(parentCommentId.toString()))
                .withQueryParam("resourceId", equalTo(occurrenceId.toString()))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(pageResponse)),
                ),
        )

        mockMvc
            .get("/api/events/$eventId/occurrences/$occurrenceId/comments") {
                param("targetId", parentCommentId.toString())
                accept = APPLICATION_JSON
            }
            .andExpect { status { isOk() } }

        verify(
            getRequestedFor(urlPathEqualTo("/internal/comments"))
                .withQueryParam("targetId", equalTo(parentCommentId.toString()))
                .withQueryParam("resourceId", equalTo(occurrenceId.toString())),
        )
    }

    test("should forward like to common-internal") {
        val userId = UUID.randomUUID()
        val eventId = createEventViaApi(userId)
        val occurrenceId = firstOccurrenceId(eventId)
        val commentId = UUID.randomUUID()
        val comment = CommentResponse(
            id = commentId,
            targetId = occurrenceId,
            resourceId = occurrenceId,
            authorUserId = userId,
            body = "Wall comment",
            createdAt = null,
        )
        stubFor(
            get(urlPathEqualTo("/internal/comments/$commentId"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(comment)),
                ),
        )
        stubFor(
            post(urlPathEqualTo("/internal/comments/$commentId/likes"))
                .willReturn(aResponse().withStatus(204)),
        )

        mockMvc
            .post("/api/events/$eventId/occurrences/$occurrenceId/comments/$commentId/likes") {
                with(jwt().jwt { it.claim("userId", userId) })
            }
            .andExpect { status { isNoContent() } }

        verify(postRequestedFor(urlPathEqualTo("/internal/comments/$commentId/likes")))
    }

    test("should forward unlike to common-internal") {
        val userId = UUID.randomUUID()
        val eventId = createEventViaApi(userId)
        val occurrenceId = firstOccurrenceId(eventId)
        val commentId = UUID.randomUUID()
        val comment = CommentResponse(
            id = commentId,
            targetId = occurrenceId,
            resourceId = occurrenceId,
            authorUserId = userId,
            body = "Wall comment",
            createdAt = null,
        )
        stubFor(
            get(urlPathEqualTo("/internal/comments/$commentId"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(comment)),
                ),
        )
        stubFor(
            delete(urlPathEqualTo("/internal/comments/$commentId/likes"))
                .willReturn(aResponse().withStatus(204)),
        )

        mockMvc
            .delete("/api/events/$eventId/occurrences/$occurrenceId/comments/$commentId/likes") {
                with(jwt().jwt { it.claim("userId", userId) })
            }
            .andExpect { status { isNoContent() } }

        verify(deleteRequestedFor(urlPathEqualTo("/internal/comments/$commentId/likes")))
    }

    afterSpec {
        eventRepository.deleteAll()
    }
})
