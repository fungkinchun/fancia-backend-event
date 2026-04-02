package com.fancia.backend.event.core.exception

import com.fancia.backend.shared.common.core.exception.DomainException

class ReservationStatusChangeAccessDeniedException(
    title: String = "Access denied for changing event reservation status",
    message: String = "Only admins can change reservation status (except withdrawal)",
    errorCode: String = "EVENT_RESERVATION_STATUS_CHANGE_ACCESS_DENIED"
) : DomainException(title, message, errorCode)