package com.example.ami.dto;

import com.example.ami.enums.CurveType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/** 按曲线类型和 UTC 采样时刻定位一次读数补正。 */
public record Correction(@NotNull CurveType curveType, @NotNull Instant sampleTime,
                         @NotNull @Digits(integer = 12, fraction = 4) BigDecimal value) {}
