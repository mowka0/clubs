package com.clubs.application

import com.clubs.generated.jooq.enums.ApplicationStatus
import com.clubs.generated.jooq.tables.references.CLUBS
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class ApplicationScheduler(
    private val applicationRepository: ApplicationRepository,
    private val dsl: DSLContext
) {

    private val log = LoggerFactory.getLogger(ApplicationScheduler::class.java)

    /**
     * Every hour: auto-reject pending applications older than 48 hours.
     * Also decreases activity_rating of the club by 0.5 for each auto-rejected application.
     */
    @Scheduled(fixedDelay = 60 * 60 * 1000L)
    fun autoRejectStalePendingApplications() {
        val cutoff = OffsetDateTime.now().minusHours(48)
        val stale = applicationRepository.findPendingOlderThan(cutoff)

        if (stale.isEmpty()) return

        stale.forEach { application ->
            applicationRepository.updateStatus(application.id, ApplicationStatus.auto_rejected)
            dsl.update(CLUBS)
                .set(CLUBS.ACTIVITY_RATING, CLUBS.ACTIVITY_RATING.minus(0.5))
                .where(CLUBS.ID.eq(application.clubId))
                .execute()
        }

        log.info("Auto-rejected ${stale.size} stale application(s) older than 48 hours")
    }
}
