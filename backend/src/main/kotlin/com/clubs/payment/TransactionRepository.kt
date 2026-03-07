package com.clubs.payment

import com.clubs.generated.jooq.enums.TransactionType
import com.clubs.generated.jooq.tables.references.TRANSACTIONS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.YearMonth
import java.util.UUID

@Repository
class TransactionRepository(private val dsl: DSLContext) {

    fun create(
        userId: UUID,
        clubId: UUID,
        membershipId: UUID?,
        amountStars: Int,
        platformFee: Int,
        organizerRevenue: Int,
        telegramPaymentId: String?,
        transactionType: TransactionType = TransactionType.subscription
    ): TransactionDto {
        return dsl.insertInto(TRANSACTIONS)
            .set(TRANSACTIONS.USER_ID, userId)
            .set(TRANSACTIONS.CLUB_ID, clubId)
            .set(TRANSACTIONS.MEMBERSHIP_ID, membershipId)
            .set(TRANSACTIONS.AMOUNT_STARS, amountStars)
            .set(TRANSACTIONS.PLATFORM_FEE, platformFee)
            .set(TRANSACTIONS.ORGANIZER_REVENUE, organizerRevenue)
            .set(TRANSACTIONS.TELEGRAM_PAYMENT_ID, telegramPaymentId)
            .set(TRANSACTIONS.TRANSACTION_TYPE, transactionType)
            .returning()
            .fetchOne()!!
            .toDto()
    }

    fun findByUserAndClub(userId: UUID, clubId: UUID): List<TransactionDto> {
        return dsl.selectFrom(TRANSACTIONS)
            .where(TRANSACTIONS.USER_ID.eq(userId))
            .and(TRANSACTIONS.CLUB_ID.eq(clubId))
            .orderBy(TRANSACTIONS.CREATED_AT.desc())
            .fetch()
            .map { it.toDto() }
    }

    fun findByClub(clubId: UUID): List<TransactionDto> {
        return dsl.selectFrom(TRANSACTIONS)
            .where(TRANSACTIONS.CLUB_ID.eq(clubId))
            .orderBy(TRANSACTIONS.CREATED_AT.desc())
            .fetch()
            .map { it.toDto() }
    }

    fun findByClubForMonth(clubId: UUID, month: YearMonth): List<TransactionDto> {
        val start = month.atDay(1).atStartOfDay().atOffset(OffsetDateTime.now().offset)
        val end = month.atEndOfMonth().atTime(23, 59, 59).atOffset(OffsetDateTime.now().offset)
        return dsl.selectFrom(TRANSACTIONS)
            .where(TRANSACTIONS.CLUB_ID.eq(clubId))
            .and(TRANSACTIONS.CREATED_AT.ge(start))
            .and(TRANSACTIONS.CREATED_AT.le(end))
            .orderBy(TRANSACTIONS.CREATED_AT.desc())
            .fetch()
            .map { it.toDto() }
    }

    fun findByClubPaged(clubId: UUID, page: Int, size: Int): List<TransactionDto> {
        return dsl.selectFrom(TRANSACTIONS)
            .where(TRANSACTIONS.CLUB_ID.eq(clubId))
            .orderBy(TRANSACTIONS.CREATED_AT.desc())
            .limit(size)
            .offset(page * size)
            .fetch()
            .map { it.toDto() }
    }

    fun countByClub(clubId: UUID): Int {
        return dsl.fetchCount(
            dsl.selectFrom(TRANSACTIONS).where(TRANSACTIONS.CLUB_ID.eq(clubId))
        )
    }

    private fun org.jooq.Record.toDto() = TransactionDto(
        id = this[TRANSACTIONS.ID]!!,
        userId = this[TRANSACTIONS.USER_ID]!!,
        clubId = this[TRANSACTIONS.CLUB_ID]!!,
        membershipId = this[TRANSACTIONS.MEMBERSHIP_ID],
        amountStars = this[TRANSACTIONS.AMOUNT_STARS]!!,
        platformFee = this[TRANSACTIONS.PLATFORM_FEE]!!,
        organizerRevenue = this[TRANSACTIONS.ORGANIZER_REVENUE]!!,
        telegramPaymentId = this[TRANSACTIONS.TELEGRAM_PAYMENT_ID],
        transactionType = this[TRANSACTIONS.TRANSACTION_TYPE]!!.name,
        createdAt = this[TRANSACTIONS.CREATED_AT] ?: OffsetDateTime.now()
    )
}
