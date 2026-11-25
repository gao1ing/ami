package com.example.ami.service;

import com.example.ami.dto.CurveSearchRequest;
import com.example.ami.dto.CurveSearchResponse;
import com.example.ami.enums.CurveType;
import com.example.ami.mapper.CurveSearchMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** 校验用量口径并分页检索曲线。 */
@Service
public class CurveSearchService {

    private final CurveSearchMapper mapper;

    public CurveSearchService(CurveSearchMapper mapper) {
        this.mapper = mapper;
    }

    /** 返回当前页及是否还有下一页。 */
    public CurveSearchResponse search(CurveSearchRequest request) {
        Instant start = request.from().toInstant();
        Instant end = request.to().toInstant();

        if (!start.isBefore(end)) {
            throw badRequest("from 必须早于 to");
        }
        if (Duration.between(start, end).compareTo(Duration.ofDays(31)) > 0) {
            throw badRequest("查询时间窗最多为 31 天");
        }

        String curveType = resolveCurveType(request);
        int size = request.size();
        // 先转 long 再乘页大小，避免大页码导致整数溢出。
        long offset = (long) (request.page() - 1) * size;

        // 多取一行判断是否有下一页；响应只包含请求的条数。
        List<CurveSearchMapper.Row> rows = mapper.search(
                request.deviceNos(), curveType,
                LocalDateTime.ofInstant(start, ZoneOffset.UTC),
                LocalDateTime.ofInstant(end, ZoneOffset.UTC),
                request.minKwh(), request.maxKwh(),
                offset, size + 1);

        List<CurveSearchResponse.Item> items = rows.stream()
                .limit(size)
                .map(row -> new CurveSearchResponse.Item(
                        row.deviceNo(), row.curveType(),
                        row.sampleTime().toInstant(ZoneOffset.UTC), row.value()))
                .toList();

        return new CurveSearchResponse(request.page(), size, rows.size() > size, items);
    }

    /** 有用量条件时限定区间电量，拒绝量纲冲突。 */
    private String resolveCurveType(CurveSearchRequest request) {
        BigDecimal min = request.minKwh();
        BigDecimal max = request.maxKwh();
        CurveType type = request.curveType();

        if (min == null && max == null) {
            return type == null ? null : type.name();
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw badRequest("minKwh 不能大于 maxKwh");
        }
        if (type != null && type != CurveType.IMPORT_KWH) {
            throw badRequest("用量条件仅适用于 IMPORT_KWH");
        }
        return CurveType.IMPORT_KWH.name();
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
