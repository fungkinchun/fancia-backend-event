package com.fancia.backend.event.core.entity

import com.fancia.backend.shared.common.core.entity.AbstractEntity
import com.fancia.backend.shared.common.social.core.entity.Link
import com.fancia.backend.shared.event.core.enums.EventLocationKind
import com.fancia.backend.shared.event.core.enums.EventVisibility
import com.fancia.backend.shared.event.core.enums.RecurrenceFrequency
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

    @Column(name = "approval_required", nullable = false)
    var approvalRequired: Boolean = true

    var startTime: LocalDateTime? = null
    var endTime: LocalDateTime? = null

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable
    @Column(name = "event_interest_groups")
    var interestGroups: MutableSet<UUID> = mutableSetOf()

    @OneToMany(mappedBy = "event", cascade = [CascadeType.ALL], orphanRemoval = true)
    val occurrences: MutableSet<EventOccurrence> = mutableSetOf()

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "event_tags", joinColumns = [JoinColumn(name = "event_id")])
    @Column(name = "tag_id")
    var tags: MutableSet<UUID> = mutableSetOf()

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

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_frequency", length = 32, nullable = false)
    var recurrenceFrequency: RecurrenceFrequency = RecurrenceFrequency.NONE

    @Column(name = "recurrence_days_mask", nullable = false)
    var recurrenceDaysMask: Int = 0

    @Column(name = "recurrence_paused_until")
    var recurrencePausedUntil: LocalDateTime? = null
}
