-- Reserved teaching devices. Re-running inserts only missing rows.
START TRANSACTION;

INSERT INTO device (device_no, site_zone) VALUES
    ('M001', 'America/New_York'), ('M002', 'America/New_York'), ('M003', 'America/New_York')
ON DUPLICATE KEY UPDATE id = device.id;

SET @m1 = (SELECT id FROM device WHERE device_no = 'M001');
SET @m2 = (SELECT id FROM device WHERE device_no = 'M002');
SET @m3 = (SELECT id FROM device WHERE device_no = 'M003');

-- Local 2026-11-25 midnight = 05:00 UTC. Missing rows represent missing samples.
INSERT INTO curve_reading (device_id, curve_type, sample_time, reading_value) VALUES
    (@m1, 'VOLTAGE_A',    '2026-11-25 04:45:00', 219.9),
    (@m1, 'VOLTAGE_A',    '2026-11-25 05:00:00', 220.1),
    (@m1, 'VOLTAGE_B',    '2026-11-25 05:00:00', 221.3),
    (@m1, 'VOLTAGE_C',    '2026-11-25 05:00:00', 220.5),
    (@m1, 'CURRENT_A',    '2026-11-25 05:00:00', 1.2),
    (@m1, 'CURRENT_B',    '2026-11-25 05:00:00', 1.3),
    (@m1, 'CURRENT_C',    '2026-11-25 05:00:00', 1.1),
    (@m1, 'ACTIVE_POWER', '2026-11-25 05:00:00', 0.76),
    (@m1, 'POWER_FACTOR', '2026-11-25 05:00:00', 0.96),
    (@m1, 'IMPORT_KWH',   '2026-11-25 05:00:00', 0),
    (@m1, 'IMPORT_KWH',   '2026-11-25 05:15:00', 1.25),
    (@m1, 'VOLTAGE_A',    '2026-11-26 05:00:00', 221.0),
    (@m2, 'IMPORT_KWH',   '2026-11-25 05:15:00', 2.5)
ON DUPLICATE KEY UPDATE reading_value = curve_reading.reading_value;

-- Snapshot as of 05:16 UTC. M002 has a fresh heartbeat awaiting processing.
INSERT INTO heartbeat_event (device_id, source_event_id, reported_at, received_at, processed_at) VALUES
    (@m1, 'hb-001', '2026-11-25 05:15:00', '2026-11-25 05:15:02', '2026-11-25 05:15:04'),
    (@m2, 'hb-001', '2026-11-25 03:00:00', '2026-11-25 03:00:01', '2026-11-25 03:00:04'),
    (@m2, 'hb-002', '2026-11-25 05:00:00', '2026-11-25 05:15:03', NULL)
ON DUPLICATE KEY UPDATE id = heartbeat_event.id;

INSERT INTO device_status (device_id, state, last_received_at) VALUES
    (@m1, 'ONLINE',  '2026-11-25 05:15:02'),
    (@m2, 'OFFLINE', '2026-11-25 03:00:01'),
    (@m3, 'NEVER',   NULL)
ON DUPLICATE KEY UPDATE state = device_status.state;
COMMIT;
