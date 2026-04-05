package com.fancia.backend.event.core.entity

import com.fancia.backend.shared.event.core.enums.EventRole
import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "event_participants")
class EventParticipant(
    @Id
    var userId: UUID
) {
    @ManyToOne
    @JoinColumn(name = "event_id")
    var event: Event? = null

    @Column(name = "role")
    var role: EventRole = EventRole.HOST
}