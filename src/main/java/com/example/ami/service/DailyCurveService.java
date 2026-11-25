package com.example.ami.service;

import com.example.ami.dto.DailyCurveResponse;
import com.example.ami.cache.DailyCurveCache;
import com.example.ami.mapper.DailyCurveMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 按站点时区查询设备日曲线。 */
@Service
public class DailyCurveService {

    private final DailyCurveMapper mapper;
    private final DailyCurveCache cache;

    public DailyCurveService(DailyCurveMapper mapper, DailyCurveCache cache) {
        this.mapper = mapper;
        this.cache = cache;
    }

    /** 读取指定设备当地业务日的实际采样点。 */
    public DailyCurveResponse findByDay(String deviceNo, LocalDate date) {
        DailyCurveMapper.Device device = mapper.findDevice(deviceNo);
        if (device == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "设备不存在");
        }

        // 用档案里的正式设备号生成键，避免查询别名和补正操作命中不同的缓存。
        Optional<DailyCurveResponse> cached = cache.get(device.deviceNo(), date);
        if (cached.isPresent()) {
            return cached.get();
        }
        ZoneId zone = ZoneId.of(device.siteZone());
        // 分别计算查询日和次日的当地零点。夏令时切换日可能只有 23 小时或长达 25 小时。
        Instant start = date.atStartOfDay(zone).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(zone).toInstant();

        List<DailyCurveMapper.Reading> readings = mapper.findReadings(
                device.id(),
                LocalDateTime.ofInstant(start, ZoneOffset.UTC),
                LocalDateTime.ofInstant(end, ZoneOffset.UTC));

        List<DailyCurveResponse.Point> points = readings.stream()
                .map(row -> new DailyCurveResponse.Point(
                        row.curveType(),
                        row.sampleTime().toInstant(ZoneOffset.UTC),
                        row.value()))
                .toList();

        DailyCurveResponse response = new DailyCurveResponse(device.deviceNo(), date, device.siteZone(), points);
        cache.put(response);
        return response;
    }
}
