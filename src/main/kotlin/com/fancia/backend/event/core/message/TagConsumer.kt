package com.fancia.backend.event.core.message

import com.fancia.backend.event.core.service.EventService
import com.fancia.backend.shared.common.tag.core.message.TagDeletedEvent
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class TagConsumer(
    private val eventService: EventService,
) {
    @KafkaListener(topics = ["tags"], groupId = "deletion")
    fun onTagDeleted(event: TagDeletedEvent) {
        eventService.removeTagFromAllEvents(event.id)
    }
}
