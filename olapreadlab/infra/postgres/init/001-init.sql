CREATE SCHEMA IF NOT EXISTS olap;

ALTER ROLE olap WITH REPLICATION;

COMMENT ON SCHEMA olap IS 'OLAP read optimization experiment objects';
