package com.fancia.backend.event.core.entity

import com.fancia.backend.shared.common.core.entity.AbstractEntity
import jakarta.persistence.*
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "events")
class Event : AbstractEntity() {
    @Column(nullable = false)
    var name: String = ""

    @Column(nullable = false)
    var description: String = ""
    var startTime: LocalDateTime? = null
    var duration: Duration? = null
    var interestGroupId: UUID? = null

    @OneToMany(mappedBy = "event", cascade = [CascadeType.ALL], orphanRemoval = true)
    val participants: MutableSet<EventParticipant> = mutableSetOf<EventParticipant>()

    @OneToMany(mappedBy = "event", cascade = [CascadeType.ALL], orphanRemoval = true)
    val reservations: MutableSet<Reservation> = mutableSetOf<Reservation>()

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable
    @Column(name = "tag", length = 100)
    val tags: MutableSet<String> = mutableSetOf()
}
 