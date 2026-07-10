package com.fancia.backend.event.core.entity

import com.fancia.backend.shared.common.core.entity.AbstractEntity
import com.fancia.backend.shared.event.core.enums.OccurrenceStatus
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "event_occurrences",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_event_occurrences_event_start", columnNames = ["event_id", "start_time"]),
    ],
)
class EventOccurrence : AbstractEntity() {
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id", nullable = false)
    var event: Event? = null

    @Column(name = "start_time", nullable = false)
    var startTime: LocalDateTime = LocalDateTime.MIN

    @Column(name = "end_time", nullable = false)
    var endTime: LocalDateTime = LocalDateTime.MIN

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: OccurrenceStatus = OccurrenceStatus.SCHEDULED

    @OneToMany(mappedBy = "occurrence", cascade = [CascadeType.ALL], orphanRemoval = true)
    val participants: MutableSet<EventParticipant> = mutableSetOf()

    @OneToMany(mappedBy = "occurrence", cascade = [CascadeType.ALL], orphanRemoval = true)
    val reservations: MutableSet<Reservation> = mutableSetOf()
}
