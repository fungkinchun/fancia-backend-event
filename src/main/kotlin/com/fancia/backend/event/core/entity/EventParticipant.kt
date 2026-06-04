package com.fancia.backend.event.core.entity

import com.fancia.backend.shared.event.core.enums.EventRole
import jakarta.persistence.*
import java.io.Serializable
import java.util.*

@Embeddable
data class EventParticipantId(
    val eventId: UUID,
    val userId: UUID
) : Serializable {
    override fun equals(other: Any?): Boolean =
        other is EventParticipantId &&
                other.eventId == eventId &&
                other.userId == userId

    override fun hashCode(): Int = Objects.hash(eventId, userId)
}

@Entity
@Table(name = "event_participants")
class EventParticipant(
    @EmbeddedId
    var id: EventParticipantId
) {
    @MapsId("eventId")
    @ManyToOne
    @JoinColumn(name = "event_id", insertable = false, updatable = false)
    var event: Event? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    var role: EventRole = EventRole.HOST
}