-- 데이터 적재 전에 적용한다. 최하위 MV 하나의 쓰기/보정 비용만 측정한다.
DROP VIEW IF EXISTS olap_clickhouse.mv_person_organ;
DROP VIEW IF EXISTS olap_clickhouse.mv_person_disease;
DROP VIEW IF EXISTS olap_clickhouse.mv_organ_disease;
DROP VIEW IF EXISTS olap_clickhouse.mv_person;
DROP VIEW IF EXISTS olap_clickhouse.mv_organ;
DROP VIEW IF EXISTS olap_clickhouse.mv_disease;
DROP VIEW IF EXISTS olap_clickhouse.mv_total;
