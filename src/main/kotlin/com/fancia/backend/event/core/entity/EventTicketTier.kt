package com.fancia.backend.event.core.entity

import com.fancia.backend.shared.common.core.entity.AbstractEntity
import com.fancia.backend.shared.event.core.entity.Event
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "event_ticket_tiers")
class EventTicketTier : AbstractEntity() {
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    var event: Event? = null

    @Column(nullable = false, length = 255)
    var name: String = ""

    @Column(name = "price_minor", nullable = false)
    var priceMinor: Long = 0

    @Column(nullable = false, length = 8)
    var currency: String = "gbp"

    @Column(name = "capacity_per_occurrence")
    var capacityPerOccurrence: Int? = null

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0
}
