-- Run against the selected ami_demo database. All DATETIME values are UTC.
CREATE TABLE IF NOT EXISTS device (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    device_no VARCHAR(64) NOT NULL,
    site_zone VARCHAR(64) NOT NULL COMMENT 'IANA zone ID',
    UNIQUE KEY uk_device_no (device_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS curve_reading (
    device_id BIGINT NOT NULL,
    curve_type VARCHAR(24) NOT NULL,
    sample_time DATETIME NOT NULL,
    reading_value DECIMAL(16,4) NOT NULL,
    PRIMARY KEY (device_id, curve_type, sample_time),
    CONSTRAINT fk_curve_device FOREIGN KEY (device_id) REFERENCES device(id),
    CONSTRAINT ck_curve_type CHECK (curve_type IN (
        'VOLTAGE_A','VOLTAGE_B','VOLTAGE_C','CURRENT_A','CURRENT_B','CURRENT_C',
        'ACTIVE_POWER','POWER_FACTOR','IMPORT_KWH'
    ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS heartbeat_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL,
    source_event_id VARCHAR(64) NOT NULL,
    reported_at DATETIME(3) NOT NULL COMMENT 'Device clock, normalized to UTC',
    received_at DATETIME(3) NOT NULL COMMENT 'Platform receipt time, UTC',
    processed_at DATETIME(3) NULL,
    UNIQUE KEY uk_heartbeat_source (device_id, source_event_id),
    KEY ix_heartbeat_pending (processed_at, id),
    KEY ix_heartbeat_device_pending (device_id, processed_at, received_at),
    CONSTRAINT fk_event_device FOREIGN KEY (device_id) REFERENCES device(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS device_status (
    device_id BIGINT NOT NULL PRIMARY KEY,
    state VARCHAR(8) NOT NULL,
    last_received_at DATETIME(3) NULL,
    KEY ix_status_expiry (state, last_received_at),
    CONSTRAINT fk_status_device FOREIGN KEY (device_id) REFERENCES device(id),
    CONSTRAINT ck_status_state CHECK (
        (state = 'NEVER' AND last_received_at IS NULL) OR
        (state IN ('ONLINE', 'OFFLINE') AND last_received_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
