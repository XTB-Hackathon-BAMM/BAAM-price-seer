package pl.bamm.priceseer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Spring Boot entry point for the Price Seer application.
 * Subscribes to real-time market prices from Kafka and publishes directional
 * predictions back to Kafka for Oracle scoring.
 */
@SpringBootApplication
@EnableScheduling
class PriceseerApplication

/** Bootstraps the Spring application context. */
fun main(args: Array<String>) {
	runApplication<PriceseerApplication>(*args)
}
