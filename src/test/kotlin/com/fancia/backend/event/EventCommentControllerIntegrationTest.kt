package com.fancia.backend.event

import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.shared.common.comment.core.dto.CommentResponse
import com.github.tomakehurst.wiremock.client.WireMock.*
import io.kotest.core.spec.style.FunSpec
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
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
        resetAllRequests()
    }

    fun createEventViaApi(userId: UUID): UUID {
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
                                    "size" to 20,
                                    "number" to 0,
                                )
                            )
                        )
                )
        )
        val responseBody = mockMvc
            .post("/api/events") {
                with(jwt().jwt { it.claim("userId", userId) })
                content = jsonMapper.writeValueAsString(
                    mapOf(
                        "name" to "Comment Test Event",
                        "description" to "Event for comment integration tests",
                        "startTime" to "2024-06-01T10:00:00",
                        "duration" to "PT2H",
                        "interestGroupId" to null,
                        "tags" to emptyList<Any>(),
                    )
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

    test("should forward event wall comment creation to common-internal") {
        val userId = UUID.randomUUID()
        val eventId = createEventViaApi(userId)
        val commentId = UUID.randomUUID()
        val commonResponse = CommentResponse(
            id = commentId,
            targetId = eventId,
            resourceId = eventId,
            authorUserId = userId,
            body = "Great event",
            createdAt = null,
        )
        stubFor(
            post(urlPathEqualTo("/internal/comments"))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(commonResponse))
                )
        )

        mockMvc
            .post("/api/events/$eventId/comments") {
                with(jwt().jwt { it.claim("userId", userId) })
                content = jsonMapper.writeValueAsString(
                    mapOf(
                        "targetId" to eventId.toString(),
                        "resourceId" to eventId.toString(),
                        "body" to "Great event",
                    )
                )
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isCreated() }
                jsonPath("$.id", `is`(commentId.toString()))
                jsonPath("$.targetId", `is`(eventId.toString()))
                jsonPath("$.resourceId", `is`(eventId.toString()))
            }

        verify(
            postRequestedFor(urlPathEqualTo("/internal/comments"))
                .withRequestBody(matchingJsonPath("$.targetId", equalTo(eventId.toString())))
                .withRequestBody(matchingJsonPath("$.resourceId", equalTo(eventId.toString())))
                .withRequestBody(matchingJsonPath("$.body", equalTo("Great event"))),
        )
    }

    test("should forward post comment creation to common-internal") {
        val userId = UUID.randomUUID()
        val eventId = createEventViaApi(userId)
        val postId = UUID.randomUUID()
        val commentId = UUID.randomUUID()
        val commonResponse = CommentResponse(
            id = commentId,
            targetId = postId,
            resourceId = postId,
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
                        .withBody(jsonMapper.writeValueAsString(commonResponse))
                )
        )

        mockMvc
            .post("/api/events/$eventId/comments") {
                with(jwt().jwt { it.claim("userId", userId) })
                content = jsonMapper.writeValueAsString(
                    mapOf(
                        "targetId" to postId.toString(),
                        "resourceId" to postId.toString(),
                        "body" to "Nice post",
                    )
                )
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isCreated() }
                jsonPath("$.id", `is`(commentId.toString()))
                jsonPath("$.resourceId", `is`(postId.toString()))
            }

        verify(
            postRequestedFor(urlPathEqualTo("/internal/comments"))
                .withRequestBody(matchingJsonPath("$.targetId", equalTo(postId.toString())))
                .withRequestBody(matchingJsonPath("$.resourceId", equalTo(postId.toString())))
                .withRequestBody(matchingJsonPath("$.body", equalTo("Nice post"))),
        )
    }

    test("should return bad request when event does not exist") {
        val missingEventId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        mockMvc
            .post("/api/events/$missingEventId/comments") {
                with(jwt().jwt { it.claim("userId", userId) })
                content = jsonMapper.writeValueAsString(
                    mapOf(
                        "targetId" to missingEventId.toString(),
                        "resourceId" to missingEventId.toString(),
                        "body" to "Should fail",
                    )
                )
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect { status { isBadRequest() } }

        verify(0, postRequestedFor(urlPathEqualTo("/internal/comments")))
    }

    test("should list event wall comments with targetId defaulting to eventId") {
        val userId = UUID.randomUUID()
        val eventId = createEventViaApi(userId)
        val pageResponse = mapOf(
            "content" to emptyList<Any>(),
            "totalElements" to 0,
            "totalPages" to 0,
            "size" to 20,
            "number" to 0,
        )
        stubFor(
            get(urlPathEqualTo("/internal/comments"))
                .withQueryParam("targetId", equalTo(eventId.toString()))
                .withQueryParam("resourceId", equalTo(eventId.toString()))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(pageResponse))
                )
        )

        mockMvc
            .get("/api/events/$eventId/comments") {
                with(jwt().jwt { it.claim("userId", userId) })
                accept = APPLICATION_JSON
            }
            .andExpect { status { isOk() } }

        verify(
            getRequestedFor(urlPathEqualTo("/internal/comments"))
                .withQueryParam("targetId", equalTo(eventId.toString()))
                .withQueryParam("resourceId", equalTo(eventId.toString())),
        )
    }

    test("should list post comments when targetId is post id") {
        val userId = UUID.randomUUID()
        val eventId = createEventViaApi(userId)
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
                .withQueryParam("resourceId", equalTo(eventId.toString()))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(pageResponse))
                )
        )

        mockMvc
            .get("/api/events/$eventId/comments") {
                with(jwt().jwt { it.claim("userId", userId) })
                param("targetId", postId.toString())
                accept = APPLICATION_JSON
            }
            .andExpect { status { isOk() } }

        verify(
            getRequestedFor(urlPathEqualTo("/internal/comments"))
                .withQueryParam("targetId", equalTo(postId.toString()))
                .withQueryParam("resourceId", equalTo(eventId.toString())),
        )
    }

    test("should list replies when targetId is parent comment id") {
        val userId = UUID.randomUUID()
        val eventId = createEventViaApi(userId)
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
                .withQueryParam("resourceId", equalTo(eventId.toString()))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(pageResponse))
                )
        )

        mockMvc
            .get("/api/events/$eventId/comments") {
                with(jwt().jwt { it.claim("userId", userId) })
                param("targetId", parentCommentId.toString())
                accept = APPLICATION_JSON
            }
            .andExpect { status { isOk() } }

        verify(
            getRequestedFor(urlPathEqualTo("/internal/comments"))
                .withQueryParam("targetId", equalTo(parentCommentId.toString()))
                .withQueryParam("resourceId", equalTo(eventId.toString())),
        )
    }

    test("should forward like to common-internal") {
        val userId = UUID.randomUUID()
        val eventId = createEventViaApi(userId)
        val commentId = UUID.randomUUID()
        val comment = CommentResponse(
            id = commentId,
            targetId = eventId,
            resourceId = eventId,
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
                        .withBody(jsonMapper.writeValueAsString(comment))
                )
        )
        stubFor(
            post(urlPathEqualTo("/internal/comments/$commentId/likes"))
                .willReturn(aResponse().withStatus(204))
        )

        mockMvc
            .post("/api/events/$eventId/comments/$commentId/likes") {
                with(jwt().jwt { it.claim("userId", userId) })
            }
            .andExpect { status { isNoContent() } }

        verify(postRequestedFor(urlPathEqualTo("/internal/comments/$commentId/likes")))
    }

    test("should forward unlike to common-internal") {
        val userId = UUID.randomUUID()
        val eventId = createEventViaApi(userId)
        val commentId = UUID.randomUUID()
        val comment = CommentResponse(
            id = commentId,
            targetId = eventId,
            resourceId = eventId,
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
                        .withBody(jsonMapper.writeValueAsString(comment))
                )
        )
        stubFor(
            delete(urlPathEqualTo("/internal/comments/$commentId/likes"))
                .willReturn(aResponse().withStatus(204))
        )

        mockMvc
            .delete("/api/events/$eventId/comments/$commentId/likes") {
                with(jwt().jwt { it.claim("userId", userId) })
            }
            .andExpect { status { isNoContent() } }

        verify(deleteRequestedFor(urlPathEqualTo("/internal/comments/$commentId/likes")))
    }

    afterSpec {
        eventRepository.deleteAll()
    }
})
