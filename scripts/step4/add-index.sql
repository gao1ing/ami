USE ami_bench;
CREATE INDEX ix_curve_type_time_device
    ON curve_reading (curve_type, sample_time, device_id);
ANALYZE TABLE curve_reading;
