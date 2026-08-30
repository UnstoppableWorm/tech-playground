-- 파편화 상태 조회 측정이 모두 끝난 뒤에만 실행한다.
SYSTEM START MERGES olap_clickhouse.agg_person_organ_disease;

ALTER TABLE olap_clickhouse.agg_person_organ_disease RESET SETTING
    parts_to_delay_insert,
    parts_to_throw_insert,
    max_parts_in_total;
