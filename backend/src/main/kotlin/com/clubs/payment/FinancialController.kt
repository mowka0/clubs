package com.clubs.payment

import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.YearMonth
import java.util.UUID

@RestController
@RequestMapping("/api/clubs")
class FinancialController(
    private val financialService: FinancialService
) {

    @GetMapping("/{clubId}/finances")
    fun getFinances(
        @PathVariable clubId: UUID,
        @RequestParam(required = false) month: String?,
        authentication: Authentication
    ): ResponseEntity<FinancialStatsDto> {
        val requesterId = UUID.fromString(authentication.principal as String)
        val yearMonth = month?.let { YearMonth.parse(it) }
        val stats = financialService.getFinancialStats(clubId, requesterId, yearMonth)
        return ResponseEntity.ok(stats)
    }

    @GetMapping("/{clubId}/transactions")
    fun getTransactions(
        @PathVariable clubId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        authentication: Authentication
    ): ResponseEntity<PagedTransactionsResponse> {
        val requesterId = UUID.fromString(authentication.principal as String)
        val response = financialService.getTransactions(clubId, requesterId, page, size)
        return ResponseEntity.ok(response)
    }
}
