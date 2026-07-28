package com.fancia.backend.event.core.entity

import com.fancia.backend.shared.event.core.enums.EventRole
import jakarta.persistence.*
import java.io.Serializable
import java.util.*

@Embeddable
data class EventParticipantId(
    val occurrenceId: UUID,
    val userId: UUID,
) : Serializable {
    override fun equals(other: Any?): Boolean =
        other is EventParticipantId &&
            other.occurrenceId == occurrenceId &&
            other.userId == userId

    override fun hashCode(): Int = Objects.hash(occurrenceId, userId)
}

@Entity
@Table(name = "event_participants")
class EventParticipant(
    @EmbeddedId
    var id: EventParticipantId,
) {
    @MapsId("occurrenceId")
    @ManyToOne
    @JoinColumn(name = "occurrence_id", insertable = false, updatable = false)
    var occurrence: EventOccurrence? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    var role: EventRole = EventRole.HOST
}
