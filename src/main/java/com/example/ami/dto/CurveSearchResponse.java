package com.example.ami.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 曲线明细页及后续页标志。 */
public record CurveSearchResponse(
        int page,
        int size,
        boolean hasNext,
        List<Item> items
) {
    public record Item(
            String deviceNo,
            String curveType,
            Instant sampleTime,
            BigDecimal value
    ) {}
}
