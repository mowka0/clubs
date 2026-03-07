package com.clubs.application

import com.clubs.generated.jooq.enums.ApplicationStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
class ApplicationController(
    private val applicationService: ApplicationService
) {

    // POST /api/clubs/{id}/apply — подача заявки
    @PostMapping("/api/clubs/{id}/apply")
    fun apply(
        @PathVariable id: UUID,
        @RequestBody request: SubmitApplicationRequest,
        authentication: Authentication
    ): ResponseEntity<ApplicationDto> {
        val userId = UUID.fromString(authentication.principal as String)
        val application = applicationService.submitApplication(userId, id, request.answerText)
        return ResponseEntity.status(201).body(application)
    }

    // GET /api/clubs/{id}/applications — список заявок (только организатор)
    @GetMapping("/api/clubs/{id}/applications")
    fun getClubApplications(
        @PathVariable id: UUID,
        @RequestParam(required = false) status: String?,
        authentication: Authentication
    ): ResponseEntity<List<ApplicationDto>> {
        val userId = UUID.fromString(authentication.principal as String)
        val applicationStatus = status?.let {
            runCatching { ApplicationStatus.valueOf(it) }.getOrNull()
        }
        val applications = applicationService.getClubApplications(id, userId, applicationStatus)
        return ResponseEntity.ok(applications)
    }

    // PUT /api/applications/{id}/approve — одобрение заявки (только организатор)
    @PutMapping("/api/applications/{id}/approve")
    fun approve(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<ApplicationDto> {
        val userId = UUID.fromString(authentication.principal as String)
        val application = applicationService.approveApplication(id, userId)
        return ResponseEntity.ok(application)
    }

    // PUT /api/applications/{id}/reject — отклонение заявки (только организатор)
    @PutMapping("/api/applications/{id}/reject")
    fun reject(
        @PathVariable id: UUID,
        @RequestBody(required = false) request: RejectApplicationRequest?,
        authentication: Authentication
    ): ResponseEntity<ApplicationDto> {
        val userId = UUID.fromString(authentication.principal as String)
        val application = applicationService.rejectApplication(id, userId, request?.reason)
        return ResponseEntity.ok(application)
    }

    // GET /api/applications/my — мои заявки
    @GetMapping("/api/applications/my")
    fun myApplications(authentication: Authentication): ResponseEntity<List<ApplicationDto>> {
        val userId = UUID.fromString(authentication.principal as String)
        return ResponseEntity.ok(applicationService.getMyApplications(userId))
    }
}
