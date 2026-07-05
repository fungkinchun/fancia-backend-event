package com.fancia.backend.event.core.support

import com.fancia.backend.event.core.entity.Event
import com.fancia.backend.event.external.CommonServiceClient
import com.fancia.backend.shared.common.tag.core.dto.TagResponse
import com.fancia.backend.shared.common.tag.core.enums.TagType
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

data class SmartMatchPreferences(
    val tagIds: Set<UUID> = emptySet(),
    val blacklistedIds: Set<UUID> = emptySet(),
    val locationLabel: String? = null,
)

data class RankedEvent(
    val event: Event,
    val score: Double,
)

@Component
class SmartMatchEventRanker(
    private val commonServiceClient: CommonServiceClient,
) {
    fun expandTagWeights(preferences: SmartMatchPreferences): Map<UUID, Double> {
        val weights = mutableMapOf<UUID, Double>()
        if (preferences.tagIds.isEmpty()) return weights

        val seedTags = commonServiceClient.getTagsByIds(preferences.tagIds)
            .filter { it.id != null && it.id in preferences.tagIds }
        for (tag in seedTags) {
            val tagId = tag.id!!
            weights.merge(tagId, EXACT_TAG_WEIGHT) { existing, added -> maxOf(existing, added) }
            expandSimilarTags(tag, weights)
        }
        return weights
    }

    fun isBlacklisted(event: Event, blacklistedIds: Set<UUID>): Boolean {
        if (blacklistedIds.isEmpty()) return false
        if (event.createdBy != null && event.createdBy in blacklistedIds) return true
        if (event.tags.any { it in blacklistedIds }) return true
        if (event.interestGroups.any { it in blacklistedIds }) return true
        return false
    }

    fun score(
        event: Event,
        tagWeights: Map<UUID, Double>,
        preferences: SmartMatchPreferences,
        now: LocalDateTime,
    ): Double {
        var score = BASE_SCORE
        for (eventTag in event.tags) {
            score += tagWeights[eventTag] ?: 0.0
        }
        if (preferences.locationLabel != null && matchesLocationLabel(event, preferences.locationLabel)) {
            score += LOCATION_BONUS
        }
        RecurringEventVisibility.nextOccurrenceStart(event, now)?.let { nextStart ->
            val daysUntil = ChronoUnit.DAYS.between(now.toLocalDate(), nextStart.toLocalDate()).coerceAtLeast(0)
            score += (SOONER_MAX_BONUS - minOf(daysUntil.toDouble(), SOONER_MAX_BONUS))
        }
        return score
    }

    fun rank(
        candidates: List<Event>,
        preferences: SmartMatchPreferences,
        now: LocalDateTime,
        schedule: Boolean,
        busyEvents: List<Event>,
        isDiscoverable: (Event) -> Boolean,
    ): List<RankedEvent> {
        val tagWeights = expandTagWeights(preferences)
        return candidates
            .asSequence()
            .filter { event ->
                isDiscoverable(event) &&
                    RecurringEventVisibility.isListable(event, now) &&
                    !isBlacklisted(event, preferences.blacklistedIds) &&
                    (!schedule || !conflictsWithSchedule(event, busyEvents, now))
            }
            .map { event -> RankedEvent(event, score(event, tagWeights, preferences, now)) }
            .sortedWith(
                compareByDescending<RankedEvent> { it.score }
                    .thenBy { RecurringEventVisibility.nextOccurrenceStart(it.event, now) ?: LocalDateTime.MAX },
            )
            .toList()
    }

    private fun expandSimilarTags(seedTag: TagResponse, weights: MutableMap<UUID, Double>) {
        val name = seedTag.name.trim()
        if (name.isEmpty()) return
        val types = linkedSetOf(seedTag.type, TagType.INTEREST, TagType.TOPIC, TagType.EVENT)
        for (type in types) {
            val similarPage = commonServiceClient.searchTags(setOf(name), type, page = 0, size = 8)
            for (similar in similarPage.content) {
                val similarId = similar.id ?: continue
                if (similarId == seedTag.id) continue
                weights.merge(similarId, SIMILAR_TAG_WEIGHT) { existing, added -> maxOf(existing, added) }
            }
        }
    }

    private fun matchesLocationLabel(event: Event, userLocationLabel: String): Boolean {
        val normalized = userLocationLabel.trim().lowercase()
        if (normalized.isBlank()) return false
        val eventLocations = listOfNotNull(event.city, event.locationLabel, event.addressLine, event.postcode)
            .joinToString(" ")
            .lowercase()
        if (eventLocations.isBlank()) return false
        return eventLocations.contains(normalized) ||
            normalized.contains(eventLocations.substringBefore(",").trim())
    }

    private fun conflictsWithSchedule(candidate: Event, busyEvents: List<Event>, now: LocalDateTime): Boolean {
        val candidateStart = RecurringEventVisibility.nextOccurrenceStart(candidate, now) ?: return false
        val candidateEnd = RecurringEventVisibility.nextOccurrenceEnd(candidate, now) ?: return false
        return busyEvents.any { busy ->
            val busyStart = busy.startTime ?: return@any false
            val busyEnd = busy.endTime ?: return@any false
            candidateStart.isBefore(busyEnd) && candidateEnd.isAfter(busyStart)
        }
    }

    companion object {
        const val BASE_SCORE = 1.0
        const val EXACT_TAG_WEIGHT = 10.0
        const val SIMILAR_TAG_WEIGHT = 3.5
        const val LOCATION_BONUS = 4.0
        const val SOONER_MAX_BONUS = 2.0
    }
}
