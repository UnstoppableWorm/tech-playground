CREATE TABLE IF NOT EXISTS olap_clickhouse.agg_person_organ_disease
(
    bucket_date Date,
    person_id UInt64,
    organ_code UInt16,
    disease_code UInt32,
    event_count Int64,
    metric_sum Int64
)
ENGINE = SummingMergeTree((event_count, metric_sum))
PARTITION BY toYYYYMM(bucket_date)
ORDER BY (bucket_date, person_id, organ_code, disease_code);

CREATE MATERIALIZED VIEW IF NOT EXISTS olap_clickhouse.mv_person_organ_disease
TO olap_clickhouse.agg_person_organ_disease AS
SELECT
    toDate(occurred_at) AS bucket_date,
    person_id,
    organ_code,
    disease_code,
    sum(toInt64(sign)) AS event_count,
    sum(metric_value * sign) AS metric_sum
FROM olap_clickhouse.medical_history_changes
GROUP BY bucket_date, person_id, organ_code, disease_code;

CREATE TABLE IF NOT EXISTS olap_clickhouse.agg_person_organ
(
    bucket_date Date,
    person_id UInt64,
    organ_code UInt16,
    event_count Int64,
    metric_sum Int64
)
ENGINE = SummingMergeTree((event_count, metric_sum))
PARTITION BY toYYYYMM(bucket_date)
ORDER BY (bucket_date, person_id, organ_code);

CREATE MATERIALIZED VIEW IF NOT EXISTS olap_clickhouse.mv_person_organ
TO olap_clickhouse.agg_person_organ AS
SELECT toDate(occurred_at) AS bucket_date, person_id, organ_code,
       sum(toInt64(sign)) AS event_count, sum(metric_value * sign) AS metric_sum
FROM olap_clickhouse.medical_history_changes
GROUP BY bucket_date, person_id, organ_code;

CREATE TABLE IF NOT EXISTS olap_clickhouse.agg_person_disease
(
    bucket_date Date,
    person_id UInt64,
    disease_code UInt32,
    event_count Int64,
    metric_sum Int64
)
ENGINE = SummingMergeTree((event_count, metric_sum))
PARTITION BY toYYYYMM(bucket_date)
ORDER BY (bucket_date, person_id, disease_code);

CREATE MATERIALIZED VIEW IF NOT EXISTS olap_clickhouse.mv_person_disease
TO olap_clickhouse.agg_person_disease AS
SELECT toDate(occurred_at) AS bucket_date, person_id, disease_code,
       sum(toInt64(sign)) AS event_count, sum(metric_value * sign) AS metric_sum
FROM olap_clickhouse.medical_history_changes
GROUP BY bucket_date, person_id, disease_code;

CREATE TABLE IF NOT EXISTS olap_clickhouse.agg_organ_disease
(
    bucket_date Date,
    organ_code UInt16,
    disease_code UInt32,
    event_count Int64,
    metric_sum Int64
)
ENGINE = SummingMergeTree((event_count, metric_sum))
PARTITION BY toYYYYMM(bucket_date)
ORDER BY (bucket_date, organ_code, disease_code);

CREATE MATERIALIZED VIEW IF NOT EXISTS olap_clickhouse.mv_organ_disease
TO olap_clickhouse.agg_organ_disease AS
SELECT toDate(occurred_at) AS bucket_date, organ_code, disease_code,
       sum(toInt64(sign)) AS event_count, sum(metric_value * sign) AS metric_sum
FROM olap_clickhouse.medical_history_changes
GROUP BY bucket_date, organ_code, disease_code;

CREATE TABLE IF NOT EXISTS olap_clickhouse.agg_person
(
    bucket_date Date,
    person_id UInt64,
    event_count Int64,
    metric_sum Int64
)
ENGINE = SummingMergeTree((event_count, metric_sum))
PARTITION BY toYYYYMM(bucket_date)
ORDER BY (bucket_date, person_id);

CREATE MATERIALIZED VIEW IF NOT EXISTS olap_clickhouse.mv_person
TO olap_clickhouse.agg_person AS
SELECT toDate(occurred_at) AS bucket_date, person_id,
       sum(toInt64(sign)) AS event_count, sum(metric_value * sign) AS metric_sum
FROM olap_clickhouse.medical_history_changes
GROUP BY bucket_date, person_id;

CREATE TABLE IF NOT EXISTS olap_clickhouse.agg_organ
(
    bucket_date Date,
    organ_code UInt16,
    event_count Int64,
    metric_sum Int64
)
ENGINE = SummingMergeTree((event_count, metric_sum))
PARTITION BY toYYYYMM(bucket_date)
ORDER BY (bucket_date, organ_code);

CREATE MATERIALIZED VIEW IF NOT EXISTS olap_clickhouse.mv_organ
TO olap_clickhouse.agg_organ AS
SELECT toDate(occurred_at) AS bucket_date, organ_code,
       sum(toInt64(sign)) AS event_count, sum(metric_value * sign) AS metric_sum
FROM olap_clickhouse.medical_history_changes
GROUP BY bucket_date, organ_code;

CREATE TABLE IF NOT EXISTS olap_clickhouse.agg_disease
(
    bucket_date Date,
    disease_code UInt32,
    event_count Int64,
    metric_sum Int64
)
ENGINE = SummingMergeTree((event_count, metric_sum))
PARTITION BY toYYYYMM(bucket_date)
ORDER BY (bucket_date, disease_code);

CREATE MATERIALIZED VIEW IF NOT EXISTS olap_clickhouse.mv_disease
TO olap_clickhouse.agg_disease AS
SELECT toDate(occurred_at) AS bucket_date, disease_code,
       sum(toInt64(sign)) AS event_count, sum(metric_value * sign) AS metric_sum
FROM olap_clickhouse.medical_history_changes
GROUP BY bucket_date, disease_code;

CREATE TABLE IF NOT EXISTS olap_clickhouse.agg_total
(
    bucket_date Date,
    event_count Int64,
    metric_sum Int64
)
ENGINE = SummingMergeTree((event_count, metric_sum))
PARTITION BY toYYYYMM(bucket_date)
ORDER BY bucket_date;

CREATE MATERIALIZED VIEW IF NOT EXISTS olap_clickhouse.mv_total
TO olap_clickhouse.agg_total AS
SELECT toDate(occurred_at) AS bucket_date,
       sum(toInt64(sign)) AS event_count, sum(metric_value * sign) AS metric_sum
FROM olap_clickhouse.medical_history_changes
GROUP BY bucket_date;
