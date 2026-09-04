package com.bankapp.cardservice.exception

import com.bankapp.cardservice.dto.ApiError
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.data.redis.serializer.SerializationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(CardNotFoundException::class)
    fun handleNotFound(
        ex: CardNotFoundException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiError> = error(HttpStatus.NOT_FOUND, ex.message, request)

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(
        ex: InsufficientFundsException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiError> = error(HttpStatus.PAYMENT_REQUIRED, ex.message, request)

    @ExceptionHandler(CannotCloseCardException::class)
    fun handleCannotClose(
        ex: CannotCloseCardException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiError> = error(HttpStatus.BAD_REQUEST, ex.message, request)

    @ExceptionHandler(CannotDeleteCardException::class)
    fun handleCannotDelete(
        ex: CannotDeleteCardException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiError> = error(HttpStatus.BAD_REQUEST, ex.message, request)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiError> {
        val message = ex.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
            .ifBlank { "Validation failed" }
        return error(HttpStatus.BAD_REQUEST, message, request)
    }

    @ExceptionHandler(SerializationException::class)
    fun handleRedisSerialization(
        ex: SerializationException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiError> {
        log.error("Redis JSON serialization failed on {}", request.requestURI, ex)
        return error(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ex.message ?: "Failed to serialize or deserialize Redis cache value",
            request,
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ApiError> {
        log.error("Unhandled exception on {}", request.requestURI, ex)
        return error(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ex.message ?: "Unexpected error",
            request,
        )
    }

    private fun error(
        status: HttpStatus,
        message: String?,
        request: HttpServletRequest,
    ): ResponseEntity<ApiError> =
        ResponseEntity.status(status).body(
            ApiError(
                status = status.value(),
                error = status.reasonPhrase,
                message = message,
                path = request.requestURI,
            ),
        )
}
