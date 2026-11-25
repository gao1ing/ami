package com.example.ami.service;

import com.example.ami.dto.StatusPage.DeviceState;
import com.example.ami.dto.StatusPage;
import com.example.ami.enums.State;
import com.example.ami.mapper.HeartbeatMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 处理心跳接收、状态推进和离线判定。 */
@Service
public class HeartbeatService {
    private final HeartbeatMapper mapper;
    private final int timeout;
    private final int batchSize;

    public HeartbeatService(HeartbeatMapper mapper, @Value("${ami.heartbeat.timeout-seconds:120}") int timeout,
                            @Value("${ami.heartbeat.batch-size:200}") int batchSize) {
        if (timeout < 1 || batchSize < 1) throw new IllegalArgumentException("心跳超时和批大小必须为正数");
        this.mapper = mapper;
        this.timeout = timeout;
        this.batchSize = batchSize;
    }

    /** 接收心跳，重复事件沿用第一次的接收时间。 */
    public void receive(String deviceNo, String eventId, Instant reportedAt) {
        int matched = mapper.receive(deviceNo, eventId, LocalDateTime.ofInstant(reportedAt, ZoneOffset.UTC));
        if (matched == 0 && !mapper.deviceExists(deviceNo)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "设备不存在");
        }
    }

    /** 在一个事务内推进快照并标记本批事件已处理。 */
    @Transactional
    public int syncBatch() {
        // 领取、更新快照、标记处理共用一个事务，任一步失败，事件留给下轮重试。
        List<Long> ids = mapper.lockPending(batchSize);
        if (!ids.isEmpty()) {
            mapper.updateSnapshots(ids, timeout);
            mapper.markProcessed(ids);
        }
        return ids.size();
    }

    /** 将超时且没有有效待处理心跳的设备标为离线。 */
    public int expire() {
        return mapper.expire(timeout);
    }

    /** 按设备 ID 游标返回一页状态。 */
    public StatusPage statuses(State state, long afterId, int size) {
        List<HeartbeatMapper.StatusPageRow> rows = mapper.statuses(state, afterId, size + 1);
        List<DeviceState> items = rows.stream().limit(size).map(row -> new DeviceState(row.id(), row.deviceNo(), row.state(),
                row.lastReceivedAt() == null ? null : row.lastReceivedAt().toInstant(ZoneOffset.UTC))).toList();
        return new StatusPage(items, rows.size() > size ? items.get(size - 1).id() : null);
    }

    /** 汇总各通信状态的设备数。 */
    public Map<State, Long> counts() {
        // 分组查询会省略数量为零的状态；接口对三种状态都返回计数。
        EnumMap<State, Long> result = new EnumMap<>(State.class);
        for (State state : State.values()) result.put(state, 0L);
        mapper.counts().forEach(count -> result.put(count.state(), count.total()));
        return result;
    }
}
