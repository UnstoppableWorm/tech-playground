SELECT
    table,
    partition,
    count() AS active_parts,
    countIf(level = 0) AS unmerged_level_zero_parts,
    max(level) AS highest_merge_level,
    sum(rows) AS rows,
    sum(bytes_on_disk) AS bytes_on_disk
FROM system.parts
WHERE database = 'olap_clickhouse'
  AND table IN ('medical_history_changes', 'agg_person_organ_disease')
  AND active
GROUP BY table, partition
ORDER BY table, partition;

SELECT
    table,
    count() AS active_parts,
    countIf(level = 0) AS unmerged_level_zero_parts,
    sum(rows) AS rows,
    sum(bytes_on_disk) AS bytes_on_disk
FROM system.parts
WHERE database = 'olap_clickhouse'
  AND table IN ('medical_history_changes', 'agg_person_organ_disease')
  AND active
GROUP BY table
ORDER BY table;
