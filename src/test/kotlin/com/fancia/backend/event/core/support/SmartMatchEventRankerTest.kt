package com.fancia.backend.event.core.support

import com.fancia.backend.event.core.entity.Event
import com.fancia.backend.event.external.CommonServiceClient
import com.fancia.backend.shared.common.tag.core.dto.TagResponse
import com.fancia.backend.shared.common.tag.core.enums.TagType
import com.fancia.backend.shared.event.core.enums.EventVisibility
import com.fancia.backend.shared.event.core.enums.RecurrenceFrequency
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.springframework.data.domain.PageImpl
import java.time.LocalDateTime
import java.util.*

class SmartMatchEventRankerTest : FunSpec({
    val hikingTagId = UUID.randomUUID()
    val yogaTagId = UUID.randomUUID()
    val similarYogaTagId = UUID.randomUUID()
    val blacklistTagId = UUID.randomUUID()
    val organizerId = UUID.randomUUID()
    val commonServiceClient =
        object : CommonServiceClient {
            override fun searchTags(
                search: Set<String>,
                type: TagType,
                page: Int,
                size: Int,
            ) = PageImpl(
                if (search.contains("hiking")) {
                    listOf(TagResponse(id = hikingTagId, name = "hiking", type = TagType.INTEREST))
                } else if (search.contains("yoga")) {
                    listOf(
                        TagResponse(id = yogaTagId, name = "yoga", type = TagType.INTEREST),
                        TagResponse(id = similarYogaTagId, name = "yogalates", type = TagType.INTEREST),
                    )
                } else {
                    emptyList()
                },
            )

            override fun createTags(
                request: com.fancia.backend.shared.common.tag.core.dto.CreateTagsRequest,
                page: Int,
                size: Int,
            ) = PageImpl(emptyList<TagResponse>())

            override fun getTagsByIds(ids: Set<UUID>) =
                ids.mapNotNull { id ->
                    when (id) {
                        hikingTagId -> TagResponse(id = hikingTagId, name = "hiking", type = TagType.INTEREST)
                        yogaTagId -> TagResponse(id = yogaTagId, name = "yoga", type = TagType.INTEREST)
                        else -> null
                    }
                }
        }
    val ranker = SmartMatchEventRanker(commonServiceClient)
    val now = LocalDateTime.of(2030, 6, 5, 9, 0)
    fun event(
        name: String,
        tags: Set<UUID> = emptySet(),
        createdBy: UUID? = null,
    ) = Event().apply {
        this.name = name
        startTime = LocalDateTime.of(2030, 6, 10, 10, 0)
        endTime = LocalDateTime.of(2030, 6, 10, 12, 0)
        this.tags = tags.toMutableSet()
        this.createdBy = createdBy
        visibility = EventVisibility.PUBLIC
        recurrenceFrequency = RecurrenceFrequency.NONE
    }

    test("exact tag match ranks above partial similar tag match") {
        val exact = event("Hiking Meetup", setOf(hikingTagId))
        val partial = event("Yoga Flow", setOf(similarYogaTagId))
        val preferences = SmartMatchPreferences(tagIds = setOf(hikingTagId, yogaTagId))
        val ranked = ranker.rank(
            candidates = listOf(partial, exact),
            preferences = preferences,
            now = now,
            schedule = false,
            busyEvents = emptyList(),
            isDiscoverable = { true },
        )

        ranked shouldHaveSize 2
        ranked.first().event.name shouldBe "Hiking Meetup"
        ranked.first().score shouldBeGreaterThan ranked.last().score
    }

    test("blacklisted tag excludes event") {
        val blocked = event("Blocked Event", setOf(blacklistTagId))
        val allowed = event("Allowed Event", setOf(hikingTagId))
        val preferences = SmartMatchPreferences(
            tagIds = setOf(hikingTagId),
            blacklistedIds = setOf(blacklistTagId),
        )
        val ranked = ranker.rank(
            candidates = listOf(blocked, allowed),
            preferences = preferences,
            now = now,
            schedule = false,
            busyEvents = emptyList(),
            isDiscoverable = { true },
        )

        ranked shouldHaveSize 1
        ranked.single().event.name shouldBe "Allowed Event"
    }

    test("blacklisted organizer excludes event") {
        val blocked = event("Organizer Blocked", createdBy = organizerId)
        val allowed = event("Other Host", setOf(hikingTagId))
        val preferences = SmartMatchPreferences(
            tagIds = setOf(hikingTagId),
            blacklistedIds = setOf(organizerId),
        )
        val ranked = ranker.rank(
            candidates = listOf(blocked, allowed),
            preferences = preferences,
            now = now,
            schedule = false,
            busyEvents = emptyList(),
            isDiscoverable = { true },
        )

        ranked shouldHaveSize 1
        ranked.single().event.name shouldBe "Other Host"
    }
})
