package com.example.ami.job;

import com.example.ami.service.HeartbeatService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 按独立周期触发心跳消费与离线检查。 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "ami.heartbeat.enabled", havingValue = "true", matchIfMissing = true)
public class HeartbeatJobs {
    private final HeartbeatService service;

    public HeartbeatJobs(HeartbeatService service) {
        this.service = service;
    }

    /** 分批消费积压心跳，批次之间检查本轮时间预算。 */
    @Scheduled(fixedDelayString = "${ami.heartbeat.sync-delay:30s}", initialDelayString = "${ami.heartbeat.sync-delay:30s}")
    public void sync() {
        long start = System.nanoTime();
        do {
            if (service.syncBatch() == 0) return;
        } while (System.nanoTime() - start < Duration.ofSeconds(2).toNanos());
    }

    /** 按配置的超时阈值检查在线设备。 */
    @Scheduled(fixedDelayString = "${ami.heartbeat.scan-delay:60s}", initialDelayString = "${ami.heartbeat.scan-delay:60s}")
    public void scan() {
        service.expire();
    }
}
