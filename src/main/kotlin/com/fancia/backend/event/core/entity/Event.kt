package com.fancia.backend.event.core.entity

import com.fancia.backend.shared.common.core.entity.AbstractEntity
import com.fancia.backend.shared.common.social.core.entity.Link
import com.fancia.backend.shared.event.core.enums.EventLocationKind
import com.fancia.backend.shared.event.core.enums.EventVisibility
import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "events")
class Event : AbstractEntity() {
    @Column(nullable = false, length = 255)
    var name: String = ""

    @Column(nullable = false, length = 4000)
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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "event_links", joinColumns = [JoinColumn(name = "event_id")])
    var links: MutableSet<Link> = mutableSetOf()

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    var locationKind: EventLocationKind? = null

    var venueId: UUID? = null

    @Column(length = 500)
    var locationLabel: String? = null

    @Column(length = 255)
    var placeId: String? = null

    var latitude: Double? = null

    var longitude: Double? = null

    @Column(length = 500)
    var addressLine: String? = null

    @Column(length = 255)
    var city: String? = null

    @Column(length = 50)
    var postcode: String? = null

    @Column(length = 100)
    var country: String? = null
}
