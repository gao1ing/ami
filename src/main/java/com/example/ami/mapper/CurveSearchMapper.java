package com.example.ami.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 执行带时间窗和可选条件的曲线明细查询。 */
@Mapper
public interface CurveSearchMapper {

    record Row(String deviceNo, String curveType,
               LocalDateTime sampleTime, BigDecimal value) {}

    /** 返回当前页及是否还有下一页。 */
    List<Row> search(
            @Param("deviceNos") List<String> deviceNos,
            @Param("curveType") String curveType,
            @Param("startUtc") LocalDateTime startUtc,
            @Param("endUtc") LocalDateTime endUtc,
            @Param("minKwh") BigDecimal minKwh,
            @Param("maxKwh") BigDecimal maxKwh,
            @Param("offset") long offset,
            @Param("limit") int limit);
}
