-- Kafka에 데이터가 들어오기 전에 적용한다.
DETACH TABLE olap_clickhouse.medical_history_kafka_to_changes;
ALTER TABLE olap_clickhouse.medical_history_kafka MODIFY SETTING
    kafka_max_block_size = 1000000,
    kafka_poll_max_batch_size = 1000000,
    kafka_flush_interval_ms = 10000;
ATTACH TABLE olap_clickhouse.medical_history_kafka_to_changes;
