package pl.bamm.priceseer.infrastructure.scheduling

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import pl.bamm.priceseer.application.PredictionApplicationService

/**
 * Cron-triggered scheduler that invokes {@link PredictionApplicationService#sendPredictions}
 * at the start of every minute to submit directional predictions to the Oracle scorer.
 */
@Component
class PredictionScheduler(
    private val predictionApplicationService: PredictionApplicationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Fires at second 0 of every minute to send predictions for all instruments.
     */
    @Scheduled(cron = "0 * * * * *")
    fun schedulePredictions() {
        log.info("Scheduler triggered — sending predictions for all instruments")
        predictionApplicationService.sendPredictions()
    }
}
