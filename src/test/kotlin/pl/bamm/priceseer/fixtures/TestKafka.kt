package pl.bamm.priceseer.fixtures

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.time.Duration

object TestKafka {

    private const val PLAINTEXT_PORT = 9093
    private const val BROKER_PORT = 29092
    private const val CONTROLLER_PORT = 9094
    private const val CLUSTER_ID = "4L6g3nShT-eMCtK--X86sw"

    // GenericContainer bypasses KafkaContainer's TC-version-specific script injection
    // which broke with TC 2.0.5 (containerIsStarting signature change). cp-kafka 7.6.0
    // handles KRaft natively via KAFKA_PROCESS_ROLES + CLUSTER_ID env vars.
    private val container: GenericContainer<*> = GenericContainer<Nothing>("confluentinc/cp-kafka:7.6.0").apply {
        withNetworkMode("host")
        withEnv("KAFKA_NODE_ID", "1")
        withEnv("KAFKA_BROKER_ID", "1")
        withEnv("KAFKA_PROCESS_ROLES", "broker,controller")
        withEnv("CLUSTER_ID", CLUSTER_ID)
        withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@localhost:$CONTROLLER_PORT")
        withEnv("KAFKA_LISTENERS", "CONTROLLER://0.0.0.0:$CONTROLLER_PORT,BROKER://0.0.0.0:$BROKER_PORT,PLAINTEXT://0.0.0.0:$PLAINTEXT_PORT")
        withEnv("KAFKA_ADVERTISED_LISTENERS", "PLAINTEXT://localhost:$PLAINTEXT_PORT,BROKER://localhost:$BROKER_PORT")
        withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "BROKER")
        withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "BROKER:PLAINTEXT,PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT")
        withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER")
        withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
        withEnv("KAFKA_OFFSETS_TOPIC_NUM_PARTITIONS", "1")
        withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
        withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
        withEnv("KAFKA_LOG_FLUSH_INTERVAL_MESSAGES", Long.MAX_VALUE.toString())
        withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0")
        waitingFor(
            Wait.forLogMessage(".*Kafka Server started.*", 1)
                .withStartupTimeout(Duration.ofSeconds(120))
        )
    }

    val bootstrapServers = "localhost:$PLAINTEXT_PORT"

    init {
        container.start()
        Runtime.getRuntime().addShutdownHook(Thread { container.stop() })
    }
}
