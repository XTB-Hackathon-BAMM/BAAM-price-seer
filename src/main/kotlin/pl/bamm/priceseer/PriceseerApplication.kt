package pl.bamm.priceseer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PriceseerApplication

fun main(args: Array<String>) {
	runApplication<PriceseerApplication>(*args)
}
