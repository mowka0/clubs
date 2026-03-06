package com.clubs.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.security.access.AccessDeniedException

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `handleNotFound returns 404 with error body`() {
        val response = handler.handleNotFound(NotFoundException("Resource not found"))
        assertEquals(404, response.statusCode.value())
        assertEquals("NOT_FOUND", response.body?.error)
        assertEquals("Resource not found", response.body?.message)
        assertEquals(404, response.body?.status)
        assertNotNull(response.body?.timestamp)
    }

    @Test
    fun `handleAccessDenied returns 403 with error body`() {
        val response = handler.handleAccessDenied(AccessDeniedException("Access denied"))
        assertEquals(403, response.statusCode.value())
        assertEquals("FORBIDDEN", response.body?.error)
        assertEquals(403, response.body?.status)
    }

    @Test
    fun `handleValidation returns 400 with error body`() {
        val response = handler.handleValidation(ValidationException("Invalid input"))
        assertEquals(400, response.statusCode.value())
        assertEquals("VALIDATION_ERROR", response.body?.error)
        assertEquals("Invalid input", response.body?.message)
        assertEquals(400, response.body?.status)
    }

    @Test
    fun `handleConflict returns 409 with error body`() {
        val response = handler.handleConflict(ConflictException("Already exists"))
        assertEquals(409, response.statusCode.value())
        assertEquals("CONFLICT", response.body?.error)
        assertEquals(409, response.body?.status)
    }

    @Test
    fun `handleGeneric returns 500 with error body`() {
        val response = handler.handleGeneric(RuntimeException("Unexpected error"))
        assertEquals(500, response.statusCode.value())
        assertEquals("INTERNAL_ERROR", response.body?.error)
        assertEquals(500, response.body?.status)
    }

    @Test
    fun `ErrorResponse timestamp is set`() {
        val errorResponse = ErrorResponse("TEST", "test message", 400)
        assertTrue(errorResponse.timestamp > 0)
    }
}
