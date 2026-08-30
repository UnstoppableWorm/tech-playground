CREATE TABLE IF NOT EXISTS olap_clickhouse.medical_history_kafka
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
    kafka_max_block_size = 100000,
    kafka_poll_max_batch_size = 100000,
    kafka_flush_interval_ms = 10000,
    kafka_handle_error_mode = 'stream';

-- append-only signed change log: INSERT/snapshot=+1, DELETE=-1, UPDATE=before -1 + after +1
CREATE TABLE IF NOT EXISTS olap_clickhouse.medical_history_changes
(
    event_id UInt64,
    person_id UInt64,
    organ_code UInt16,
    disease_code UInt32,
    occurred_at DateTime64(3, 'UTC'),
    metric_value Int64,
    sign Int8,
    operation LowCardinality(String),
    source_lsn UInt64,
    ingested_at DateTime64(3, 'UTC') DEFAULT now64(3)
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(occurred_at)
ORDER BY (occurred_at, person_id, organ_code, disease_code, event_id, source_lsn, sign);

CREATE MATERIALIZED VIEW IF NOT EXISTS olap_clickhouse.medical_history_kafka_to_changes
TO olap_clickhouse.medical_history_changes
AS
WITH
    JSONExtractString(raw, 'op') AS op,
    JSONExtractUInt(JSONExtractRaw(raw, 'source'), 'lsn') AS lsn,
    arrayJoin(
        multiIf(
            op = 'u',
            [tuple(JSONExtractRaw(raw, 'before'), toInt8(-1)), tuple(JSONExtractRaw(raw, 'after'), toInt8(1))],
            op = 'd',
            [tuple(JSONExtractRaw(raw, 'before'), toInt8(-1))],
            [tuple(JSONExtractRaw(raw, 'after'), toInt8(1))]
        )
    ) AS change,
    tupleElement(change, 1) AS row_json
SELECT
    JSONExtractUInt(row_json, 'event_id') AS event_id,
    JSONExtractUInt(row_json, 'person_id') AS person_id,
    toUInt16(JSONExtractUInt(row_json, 'organ_code')) AS organ_code,
    toUInt32(JSONExtractUInt(row_json, 'disease_code')) AS disease_code,
    parseDateTime64BestEffort(JSONExtractString(row_json, 'occurred_at'), 3, 'UTC') AS occurred_at,
    JSONExtractInt(row_json, 'metric_value') AS metric_value,
    tupleElement(change, 2) AS sign,
    op AS operation,
    lsn AS source_lsn,
    now64(3) AS ingested_at
FROM olap_clickhouse.medical_history_kafka
WHERE op IN ('r', 'c', 'u', 'd');
