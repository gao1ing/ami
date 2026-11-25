package com.example.ami.service;

import com.example.ami.dto.Correction;
import com.example.ami.cache.DailyCurveCache;
import com.example.ami.mapper.DailyCurveMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;

/** 补正已有采样点并通知缓存失效。 */
@Service
public class ReadingCorrectionService {
    record CorrectedDay(String deviceNo, LocalDate date) {}

    private final DailyCurveMapper mapper;
    private final ApplicationEventPublisher events;
    private final DailyCurveCache cache;

    public ReadingCorrectionService(DailyCurveMapper mapper, ApplicationEventPublisher events, DailyCurveCache cache) {
        this.mapper = mapper;
        this.events = events;
        this.cache = cache;
    }

    /** 更新已有采样点，设备或读数不存在时返回 404。 */
    @Transactional
    public void correct(String deviceNo, Correction correction) {
        DailyCurveMapper.Device device = mapper.findDevice(deviceNo);
        if (device == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "设备不存在");
        Instant instant = correction.sampleTime();
        if (instant.getNano() != 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采样时刻须为整秒");
        LocalDateTime utc = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        int matched = mapper.updateReading(device.id(), correction.curveType().name(), utc, correction.value());
        if (matched == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "采样点不存在");
        // 用采样时刻折算站点日期。今天补正昨天的数据，需要清理昨天的缓存。
        events.publishEvent(new CorrectedDay(device.deviceNo(), instant.atZone(ZoneId.of(device.siteZone())).toLocalDate()));
    }

    /** 事务提交成功后清理该设备当天的缓存。 */
    @TransactionalEventListener
    public void invalidate(CorrectedDay day) {
        cache.evict(day.deviceNo(), day.date());
    }
}
