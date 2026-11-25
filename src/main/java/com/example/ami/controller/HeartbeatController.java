package com.example.ami.controller;

import com.example.ami.service.HeartbeatService;
import com.example.ami.dto.StatusPage;
import com.example.ami.enums.State;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/** 接收设备心跳并提供状态查询接口。 */
@RestController
@RequestMapping("/devices")
public class HeartbeatController {
    public record Heartbeat(@NotBlank @Size(max = 64) String eventId, @NotNull Instant reportedAt) {}

    private final HeartbeatService service;

    public HeartbeatController(HeartbeatService service) {
        this.service = service;

    }

    /** 接收心跳，重复事件沿用第一次的接收时间。 */
    @PostMapping("/{deviceNo}/heartbeats")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void receive(@PathVariable String deviceNo, @Valid @RequestBody Heartbeat event) {
        service.receive(deviceNo, event.eventId(), event.reportedAt());
    }

    /** 按设备 ID 游标返回一页状态。 */
    @GetMapping("/status")
    public StatusPage statuses(@RequestParam(required = false) State state,
                         @RequestParam(defaultValue = "0") @PositiveOrZero long afterId,
                         @RequestParam(defaultValue = "100") @Min(1) @Max(200) int size) {
        return service.statuses(state, afterId, size);
    }

    /** 汇总各通信状态的设备数。 */
    @GetMapping("/status/summary")
    public Map<State, Long> counts() {
        return service.counts();
    }
}
