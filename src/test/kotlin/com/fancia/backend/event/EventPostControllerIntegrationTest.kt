package com.fancia.backend.event

import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.shared.common.post.core.dto.PostMediaResponse
import com.fancia.backend.shared.common.post.core.dto.PostResponse
import com.fancia.backend.shared.common.post.core.enums.PostMediaType
import com.fancia.backend.shared.common.tag.core.entity.Tag
import com.fancia.backend.shared.common.tag.core.enums.TagType
import com.github.tomakehurst.wiremock.client.WireMock.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
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
class EventPostControllerIntegrationTest(
    private val mockMvc: MockMvc,
    private val eventRepository: EventRepository,
    private val jsonMapper: JsonMapper,
    private val wiremock: WireMockContainer,
    private val entityManager: EntityManager,
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

    fun persistTopicTag(name: String): Tag {
        val tag = Tag(name = name, type = TagType.TOPIC)
        entityManager.persist(tag)
        entityManager.flush()
        return tag
    }

    fun stubTopicTag(tag: Tag) {
        stubFor(
            get(urlPathEqualTo("/api/tags"))
                .withQueryParam("search", equalTo(tag.name))
                .withQueryParam("type", equalTo("TOPIC"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            jsonMapper.writeValueAsString(
                                mapOf(
                                    "content" to listOf(
                                        mapOf(
                                            "id" to tag.id.toString(),
                                            "name" to tag.name,
                                            "type" to "TOPIC",
                                        ),
                                    ),
                                    "totalElements" to 1,
                                    "totalPages" to 1,
                                    "size" to 20,
                                    "number" to 0,
                                ),
                            ),
                        ),
                ),
        )
    }

    fun createEventViaApi(userId: UUID): UUID {
        val testInterestGroupId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val tagName = "test"
        val tag = persistTopicTag(tagName)
        stubTopicTag(tag)
        val responseBody = mockMvc
            .post("/api/events") {
                with(jwt().jwt { it.claim("userId", userId) })
                content = jsonMapper.writeValueAsString(
                    mapOf(
                        "name" to "Post Test Event",
                        "description" to "Event for post integration tests",
                        "startTime" to "2024-06-01T10:00:00",
                        "duration" to "PT2H",
                        "interestGroups" to listOf(testInterestGroupId),
                        "tags" to listOf(mapOf("name" to tagName, "type" to "TOPIC")),
                        "visibility" to "PUBLIC",
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

    test("should forward featured post creation to common-internal with featured and pinned fields") {
        val userId = UUID.randomUUID()
        val eventId = createEventViaApi(userId)
        val postId = UUID.randomUUID()
        val commonResponse = PostResponse(
            id = postId,
            targetId = eventId,
            authorUserId = userId,
            body = null,
            media = listOf(
                PostMediaResponse(
                    objectKey = "tmp/1d61b8be-46d4-4131-b9e5-5c30515c58b4.jpg",
                    mediaType = PostMediaType.IMAGE,
                    sortOrder = 0,
                ),
                PostMediaResponse(
                    objectKey = "tmp/e63f5417-de54-45b9-a793-62b7ebda8050.jpg",
                    mediaType = PostMediaType.IMAGE,
                    sortOrder = 1,
                ),
            ),
            featured = true,
            pinned = false,
            createdAt = null,
        )
        stubFor(
            post(urlPathEqualTo("/internal/posts"))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(commonResponse))
                )
        )
        val requestBody = mapOf(
            "body" to null,
            "media" to listOf(
                mapOf(
                    "objectKey" to "tmp/1d61b8be-46d4-4131-b9e5-5c30515c58b4.jpg",
                    "mediaType" to "image",
                ),
                mapOf(
                    "objectKey" to "tmp/e63f5417-de54-45b9-a793-62b7ebda8050.jpg",
                    "mediaType" to "image",
                ),
            ),
            "featured" to true,
            "pinned" to false,
        )
        val responseBody = mockMvc
            .post("/api/events/$eventId/posts") {
                with(jwt().jwt { it.claim("userId", userId) })
                content = jsonMapper.writeValueAsString(requestBody)
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isCreated() }
                jsonPath("$.id", `is`(postId.toString()))
                jsonPath("$.targetId", `is`(eventId.toString()))
                jsonPath("$.featured", `is`(true))
                jsonPath("$.pinned", `is`(false))
                jsonPath("$.media.length()", `is`(2))
            }
            .andReturn()
            .response
            .contentAsString
        val response = jsonMapper.readValue(responseBody, object : TypeReference<PostResponse>() {})
        response.featured shouldBe true
        response.pinned shouldBe false

        verify(
            postRequestedFor(urlPathEqualTo("/internal/posts"))
                .withRequestBody(matchingJsonPath("$.targetId", equalTo(eventId.toString())))
                .withRequestBody(matchingJsonPath("$.authorUserId", equalTo(userId.toString())))
                .withRequestBody(matchingJsonPath("$.featured", equalTo("true")))
                .withRequestBody(matchingJsonPath("$.pinned", equalTo("false")))
                .withRequestBody(matchingJsonPath("$.media.length()", equalTo("2"))),
        )
    }


    test("should return bad request when event does not exist") {
        val missingEventId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        mockMvc
            .post("/api/events/$missingEventId/posts") {
                with(jwt().jwt { it.claim("userId", userId) })
                content = jsonMapper.writeValueAsString(
                    mapOf(
                        "body" to "hello",
                        "media" to emptyList<Any>(),
                        "featured" to false,
                        "pinned" to false,
                    )
                )
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect { status { isBadRequest() } }

        verify(0, postRequestedFor(urlPathEqualTo("/internal/posts")))
    }

    afterSpec {
        eventRepository.deleteAll()
    }
})
