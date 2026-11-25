package com.example.ami.mapper;

import com.example.ami.enums.State;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 读写心跳事件及设备状态快照。 */
@Mapper
public interface HeartbeatMapper {
    record Count(State state, long total) {}

    /** 接收心跳，重复事件沿用第一次的接收时间。 */
    int receive(@Param("deviceNo") String deviceNo, @Param("eventId") String eventId,
    /** 检查设备是否已经建档。 */
                @Param("reported") LocalDateTime reported);
    boolean deviceExists(String deviceNo);
    /** 锁定待处理事件，跳过其他事务已经领取的行。 */
    List<Long> lockPending(int limit);
    /** 取每台设备本批最新接收时间推进快照。 */
    void updateSnapshots(@Param("ids") List<Long> ids, @Param("timeout") int timeout);
    /** 记录已成功更新快照的事件。 */
    void markProcessed(@Param("ids") List<Long> ids);
    /** 将超时且没有有效待处理心跳的设备标为离线。 */
    int expire(int timeout);
    /** 按设备 ID 游标返回一页状态。 */
    List<StatusPageRow> statuses(@Param("state") State state, @Param("afterId") long afterId, @Param("limit") int limit);
    /** 汇总各通信状态的设备数。 */
    List<Count> counts();
    record StatusPageRow(long id, String deviceNo, State state, LocalDateTime lastReceivedAt) {}
}
