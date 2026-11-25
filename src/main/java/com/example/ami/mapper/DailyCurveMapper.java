package com.example.ami.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 查询设备档案，读取或更新采样点。 */
@Mapper
public interface DailyCurveMapper {

    record Device(long id, String deviceNo, String siteZone) {}

    record Reading(String curveType, LocalDateTime sampleTime, BigDecimal value) {}

    /** 按业务编号查设备及站点时区。 */
    Device findDevice(@Param("deviceNo") String deviceNo);

    /** 按采样点完整主键更新读数。 */
    int updateReading(@Param("deviceId") long deviceId, @Param("curveType") String curveType,
                      @Param("sampleTime") LocalDateTime sampleTime, @Param("value") BigDecimal value);

    /** 读取左闭右开 UTC 窗口内的采样点。 */
    List<Reading> findReadings(
            @Param("deviceId") long deviceId,
            @Param("startUtc") LocalDateTime startUtc,
            @Param("endUtc") LocalDateTime endUtc);
}
