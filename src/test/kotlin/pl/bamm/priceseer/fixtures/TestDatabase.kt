package pl.bamm.priceseer.fixtures

import org.flywaydb.core.Flyway
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.time.Duration

object TestDatabase {

    private const val PORT = 15433
    private const val DATABASE = "test"
    private const val USERNAME = "test"
    private const val PASSWORD = "test"
    val jdbcUrl = "jdbc:postgresql://localhost:$PORT/$DATABASE"

    private val container: GenericContainer<*> = GenericContainer<Nothing>("postgres:16-alpine").apply {
        withNetworkMode("host")
        withEnv("POSTGRES_USER", USERNAME)
        withEnv("POSTGRES_PASSWORD", PASSWORD)
        withEnv("POSTGRES_DB", DATABASE)
        withEnv("PGPORT", PORT.toString())
        waitingFor(
            Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2)
                .withStartupTimeout(Duration.ofSeconds(60))
        )
    }

    init {
        container.start()
        Runtime.getRuntime().addShutdownHook(Thread { container.stop() })
    }

    fun newJdbcTemplate(): JdbcTemplate {
        val ds = DriverManagerDataSource().apply {
            setDriverClassName("org.postgresql.Driver")
            url = jdbcUrl
            username = USERNAME
            password = PASSWORD
        }
        return JdbcTemplate(ds)
    }

    fun resetSchema() {
        Flyway.configure()
            .dataSource(jdbcUrl, USERNAME, PASSWORD)
            .locations("classpath:db/migration")
            .cleanDisabled(false)
            .load()
            .apply { clean(); migrate() }
    }
}
