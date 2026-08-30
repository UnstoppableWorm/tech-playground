CREATE TABLE olap.medical_history (
    event_id BIGINT PRIMARY KEY,
    person_id BIGINT NOT NULL,
    organ_code SMALLINT NOT NULL,
    disease_code INTEGER NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    metric_value BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

-- UPDATE/DELETE CDC 이벤트에도 보정 전 전체 차원과 측정값을 포함한다.
ALTER TABLE olap.medical_history REPLICA IDENTITY FULL;

CREATE INDEX medical_history_occurred_at_idx
    ON olap.medical_history (occurred_at);

CREATE TABLE olap.agg_person_organ_disease (
    bucket_date DATE NOT NULL,
    person_id BIGINT NOT NULL,
    organ_code SMALLINT NOT NULL,
    disease_code INTEGER NOT NULL,
    event_count BIGINT NOT NULL,
    metric_sum NUMERIC(38, 0) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (bucket_date, person_id, organ_code, disease_code)
);

CREATE TABLE olap.aggregation_checkpoint (
    model_key VARCHAR(100) NOT NULL,
    view_key VARCHAR(100) NOT NULL,
    pipeline VARCHAR(40) NOT NULL,
    covered_until TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (model_key, view_key, pipeline)
);

COMMENT ON COLUMN olap.aggregation_checkpoint.covered_until IS
    'Exclusive UTC event-time boundary; alignment is validated against the registered view bucket';

CREATE PUBLICATION olap_publication
    FOR TABLE olap.medical_history
    WITH (publish = 'insert, update, delete');
