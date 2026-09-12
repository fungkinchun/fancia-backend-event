package com.fancia.backend.event.core.service

import com.fancia.backend.event.core.repository.EventParticipantRepository
import com.fancia.backend.event.core.repository.ReservationRepository
import com.fancia.backend.shared.common.redis.RedisQueryCache
import com.fancia.backend.shared.event.core.repository.EventRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserResourceCleanupService(
    private val eventRepository: EventRepository,
    private val reservationRepository: ReservationRepository,
    private val eventParticipantRepository: EventParticipantRepository,
    private val redisQueryCache: ObjectProvider<RedisQueryCache>,
) {
    @Transactional
    fun deleteResourcesForUser(userId: UUID) {
        reservationRepository.findAllByIdUserId(userId).forEach { reservationRepository.delete(it) }
        eventParticipantRepository.findAllByIdUserId(userId).forEach { eventParticipantRepository.delete(it) }
        eventRepository.findByCreatedBy(userId).forEach { eventRepository.delete(it) }
        redisQueryCache.ifAvailable?.evictByPrefix(BROWSE_PREFIX)
        redisQueryCache.ifAvailable?.evictByPrefix(MATCH_PREFIX)
    }

    companion object {
        private const val BROWSE_PREFIX = "event:browse:"
        private const val MATCH_PREFIX = "event:match:"
    }
}
