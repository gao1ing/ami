package com.example.ami.dto;

import com.example.ami.enums.CurveType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** 跨设备曲线检索条件；省略设备列表表示查询所有设备。 */
public record CurveSearchRequest(
        @Size(min = 1, max = 100)
        List<@NotBlank @Size(max = 64) String> deviceNos,

        CurveType curveType,

        @NotNull OffsetDateTime from,
        @NotNull OffsetDateTime to,

        @PositiveOrZero @Digits(integer = 12, fraction = 4) BigDecimal minKwh,
        @PositiveOrZero @Digits(integer = 12, fraction = 4) BigDecimal maxKwh,

        @NotNull @Min(1) Integer page,
        @NotNull @Min(1) @Max(100) Integer size
) {}
