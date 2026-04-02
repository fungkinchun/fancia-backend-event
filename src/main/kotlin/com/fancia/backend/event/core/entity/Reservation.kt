package com.fancia.backend.event.core.entity

import jakarta.persistence.*
import java.io.Serializable
import java.util.*

@Embeddable
data class ReservationId(
    @Column(name = "event_id")
    var eventId: UUID? = null,
    @Column(name = "user_id")
    var userId: UUID? = null,
) : Serializable {
    override fun equals(other: Any?): Boolean =
        other is ReservationId && other.eventId == eventId && other.userId == userId

    override fun hashCode(): Int = Objects.hash(eventId, userId)
}

@Entity
@Table(name = "reservations")
class Reservation(
    @EmbeddedId
    var id: ReservationId? = null
) {
    @MapsId("eventId")
    @ManyToOne
    @JoinColumn(name = "event_id", insertable = false, updatable = false)
    var event: Event? = null
    var guests: Int = 0
    var payload: String = ""

    @Enumerated(EnumType.STRING)
    var status: ReservationStatus? = ReservationStatus.PENDING
}