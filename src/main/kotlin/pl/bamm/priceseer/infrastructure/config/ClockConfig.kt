package pl.bamm.priceseer.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * Provides a system UTC {@link Clock} bean for time-dependent strategy logic,
 * making it replaceable in tests.
 */
@Configuration
class ClockConfig {

    /** @return a UTC system clock */
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
