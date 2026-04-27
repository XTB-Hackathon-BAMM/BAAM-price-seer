package pl.bamm.priceseer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class PriceseerApplication

fun main(args: Array<String>) {
	runApplication<PriceseerApplication>(*args)
}
