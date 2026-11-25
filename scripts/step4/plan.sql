USE ami_bench;
EXPLAIN ANALYZE
SELECT d.device_no, r.curve_type, r.sample_time, r.reading_value
FROM curve_reading r JOIN device d ON d.id = r.device_id
WHERE r.sample_time >= '2026-11-07 10:00:00' AND r.sample_time < '2026-11-07 10:15:00'
  AND r.curve_type = 'IMPORT_KWH' AND r.reading_value >= 1
ORDER BY r.sample_time, r.device_id, r.curve_type
LIMIT 21 OFFSET 0;
