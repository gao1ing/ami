package com.example.ami.enums;

/** 采样点类型；IMPORT_KWH 表示一个采集区间的输入电量。 */
public enum CurveType {
    VOLTAGE_A, VOLTAGE_B, VOLTAGE_C,
    CURRENT_A, CURRENT_B, CURRENT_C,
    ACTIVE_POWER, POWER_FACTOR, IMPORT_KWH
}
