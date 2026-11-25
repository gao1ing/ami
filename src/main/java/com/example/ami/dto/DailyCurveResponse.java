package com.example.ami.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 设备当地一天内实际收到的采样点。 */
public record DailyCurveResponse(
        String deviceNo,
        LocalDate date,
        String zone,
        List<Point> points
) {
    public record Point(String curveType, Instant sampleTime, BigDecimal value) {}
}
