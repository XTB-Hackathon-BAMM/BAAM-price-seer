package pl.bamm.priceseer.infrastructure.scheduling

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import pl.bamm.priceseer.application.PredictionApplicationService

@Component
class PredictionScheduler(
    private val predictionApplicationService: PredictionApplicationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 * * * * *")
    fun schedulePredictions() {
        log.info("Scheduler triggered — sending predictions for all instruments")
        predictionApplicationService.sendPredictions()
    }
}
