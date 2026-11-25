package com.example.ami.dto;

import com.example.ami.enums.State;

import java.time.Instant;
import java.util.List;

/** 按设备 ID 翻页的状态快照，末页游标为空。 */
public record StatusPage(List<DeviceState> items, Long nextAfterId) {
    public record DeviceState(long id, String deviceNo, State state, Instant lastReceivedAt) {}
}
