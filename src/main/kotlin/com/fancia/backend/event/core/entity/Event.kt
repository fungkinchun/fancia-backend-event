package com.fancia.backend.event.core.entity

import com.fancia.backend.shared.common.core.entity.AbstractEntity
import com.fancia.backend.shared.event.core.enums.EventVisibility
import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "events")
class Event : AbstractEntity() {
    @Column(nullable = false)
    var name: String = ""

    @Column(nullable = false)
    var description: String = ""

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var visibility: EventVisibility = EventVisibility.PUBLIC
    var startTime: LocalDateTime? = null
    var endTime: LocalDateTime? = null

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable
    @Column(name = "event_interest_groups")
    var interestGroups: MutableSet<UUID> = mutableSetOf()

    @OneToMany(mappedBy = "event", cascade = [CascadeType.ALL], orphanRemoval = true)
    val participants: MutableSet<EventParticipant> = mutableSetOf<EventParticipant>()

    @OneToMany(mappedBy = "event", cascade = [CascadeType.ALL], orphanRemoval = true)
    val reservations: MutableSet<Reservation> = mutableSetOf<Reservation>()

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable
    @Column(name = "tag", length = 100)
    var tags: MutableSet<String> = mutableSetOf()
}
 