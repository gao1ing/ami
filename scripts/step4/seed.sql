-- Dedicated benchmark database; load 01-schema.sql here first.
USE ami_bench;
CREATE TEMPORARY TABLE seq (n INT PRIMARY KEY);
INSERT INTO seq
WITH RECURSIVE nums(n) AS (
    SELECT 0 UNION ALL SELECT n + 1 FROM nums WHERE n < 99
)
SELECT n FROM nums;

INSERT INTO device (device_no, site_zone)
SELECT CONCAT('BENCH-', LPAD(n, 3, '0')), 'UTC' FROM seq
ON DUPLICATE KEY UPDATE id = device.id;

CREATE TEMPORARY TABLE types (code VARCHAR(24) PRIMARY KEY);
INSERT INTO types VALUES ('VOLTAGE_A'),('VOLTAGE_B'),('VOLTAGE_C'),
    ('CURRENT_A'),('CURRENT_B'),('CURRENT_C'),('ACTIVE_POWER'),('POWER_FACTOR'),('IMPORT_KWH');

INSERT INTO curve_reading (device_id, curve_type, sample_time, reading_value)
WITH RECURSIVE nums(n) AS (
    SELECT 0 UNION ALL SELECT n + 1 FROM nums WHERE n < 99
)
SELECT d.id, t.code,
       TIMESTAMPADD(MINUTE, (day_no.n * 96 + slot.n) * 15, '2026-11-01 00:00:00'),
       CASE WHEN t.code LIKE 'VOLTAGE%' THEN 220 + MOD(slot.n, 5)
            WHEN t.code LIKE 'CURRENT%' THEN 1.5
            WHEN t.code = 'ACTIVE_POWER' THEN 0.9
            WHEN t.code = 'POWER_FACTOR' THEN 0.95
            ELSE MOD(meter.n + slot.n, 40) / 10 END
FROM nums meter
JOIN device d ON d.device_no = CONCAT('BENCH-', LPAD(meter.n, 3, '0'))
CROSS JOIN nums day_no CROSS JOIN nums slot CROSS JOIN types t
WHERE day_no.n < 7 AND slot.n < 96
ON DUPLICATE KEY UPDATE reading_value = curve_reading.reading_value;
DROP TEMPORARY TABLE seq, types;
ANALYZE TABLE device, curve_reading;
SELECT COUNT(*) AS curve_rows FROM curve_reading;
