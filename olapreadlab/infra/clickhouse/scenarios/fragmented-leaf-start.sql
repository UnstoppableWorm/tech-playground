-- leaf-only.sql 적용 후, Debezium connector를 시작하기 전에 실행한다.
-- 1만 block 케이스에서 월별 약 1만 part를 의도적으로 허용한다.
ALTER TABLE olap_clickhouse.agg_person_organ_disease MODIFY SETTING
    parts_to_delay_insert = 20000,
    parts_to_throw_insert = 25000,
    max_parts_in_total = 300000;

SYSTEM STOP MERGES olap_clickhouse.agg_person_organ_disease;
