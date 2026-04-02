package com.fancia.backend.event.core.controller

import com.fancia.backend.event.config.ApplicationProperties
import com.fancia.backend.shared.common.core.exception.DomainException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import java.net.URI

@RestControllerAdvice
class ValidationHandler(
    private val applicationProperties: ApplicationProperties
) {
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handle(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, Any>> {
        val errors = ex.bindingResult.allErrors.map { it.defaultMessage ?: "error" }

        return ResponseEntity(
            mapOf(
                "status" to 400,
                "errors" to errors
            ),
            HttpStatus.BAD_REQUEST
        )
    }

    @ExceptionHandler(DomainException::class)
    fun handleError(ex: DomainException, request: WebRequest): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message)
        applicationProperties.baseUrl?.let {
            problem.type = URI.create(it)
        }
        problem.title = ex.title
        problem.setProperty("errorCode", ex.errorCode)
        problem.instance = URI.create((request as ServletWebRequest).request.requestURI)
        return problem
    }
}