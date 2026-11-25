-- Baseline checks for the unmodified fixture.
SET @m1 = (SELECT id FROM device WHERE device_no = 'M001');

-- Expect 10 rows: nine curves at local midnight, one at 00:15.
SELECT curve_type, sample_time, reading_value
FROM curve_reading
WHERE device_id = @m1
  AND sample_time >= '2026-11-25 05:00:00'
  AND sample_time <  '2026-11-26 05:00:00'
ORDER BY sample_time, curve_type;

-- Interval usage >= 1 kWh: M001 1.2500 and M002 2.5000.
SELECT d.device_no, r.sample_time, r.reading_value
FROM curve_reading r JOIN device d ON d.id = r.device_id
WHERE d.device_no IN ('M001','M002') AND r.curve_type = 'IMPORT_KWH'
  AND r.sample_time >= '2026-11-25 05:00:00' AND r.sample_time < '2026-11-26 05:00:00'
  AND r.reading_value >= 1
ORDER BY r.sample_time, d.device_no;

-- Expect ONLINE/0 pending, OFFLINE/1 pending, NEVER/0 pending.
SELECT d.device_no, s.state, s.last_received_at, COUNT(e.id) AS pending
FROM device d JOIN device_status s ON s.device_id = d.id
LEFT JOIN heartbeat_event e ON e.device_id = d.id AND e.processed_at IS NULL
WHERE d.device_no IN ('M001','M002','M003')
GROUP BY d.device_no, s.state, s.last_received_at
ORDER BY d.device_no;
