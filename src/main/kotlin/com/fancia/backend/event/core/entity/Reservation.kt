package com.fancia.backend.event.core.entity

import com.fancia.backend.shared.event.core.enums.ReservationStatus
import jakarta.persistence.*
import java.io.Serializable
import java.util.*

@Embeddable
data class ReservationId(
    @Column(name = "occurrence_id")
    var occurrenceId: UUID? = null,
    @Column(name = "user_id")
    var userId: UUID? = null,
) : Serializable {
    override fun equals(other: Any?): Boolean =
        other is ReservationId && other.occurrenceId == occurrenceId && other.userId == userId

    override fun hashCode(): Int = Objects.hash(occurrenceId, userId)
}

@Entity
@Table(name = "reservations")
class Reservation(
    @EmbeddedId
    var id: ReservationId? = null,
) {
    @MapsId("occurrenceId")
    @ManyToOne
    @JoinColumn(name = "occurrence_id", insertable = false, updatable = false)
    var occurrence: EventOccurrence? = null
    var guests: Int = 0

    @Column(length = 4000)
    var payload: String = ""

    @Enumerated(EnumType.STRING)
    var status: ReservationStatus? = ReservationStatus.PENDING
}
