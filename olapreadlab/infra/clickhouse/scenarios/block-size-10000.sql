-- Kafka Engine settings cannot be altered in place. Apply this before registering Debezium.
DETACH TABLE IF EXISTS olap_clickhouse.medical_history_kafka_to_changes;
DROP TABLE IF EXISTS olap_clickhouse.medical_history_kafka;

CREATE TABLE olap_clickhouse.medical_history_kafka
(
    raw String
)
ENGINE = Kafka
SETTINGS
    kafka_broker_list = 'kafka:9092',
    kafka_topic_list = 'olap.olap.medical_history',
    kafka_group_name = 'clickhouse-medical-history-v1',
    kafka_format = 'JSONAsString',
    kafka_num_consumers = 1,
    kafka_max_block_size = 10000,
    kafka_poll_max_batch_size = 10000,
    kafka_flush_interval_ms = 10000,
    kafka_handle_error_mode = 'stream';

ATTACH TABLE olap_clickhouse.medical_history_kafka_to_changes;
