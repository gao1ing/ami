package com.example.ami.controller;

import com.example.ami.service.DailyCurveService;
import com.example.ami.dto.DailyCurveResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** 提供设备当地日曲线查询接口。 */
@RestController
@RequestMapping("/devices")
public class DailyCurveController {

    private final DailyCurveService service;

    public DailyCurveController(DailyCurveService service) {
        this.service = service;
    }

    /** 按设备编号和当地日期返回读数。 */
    @GetMapping("/{deviceNo}/daily-curves")
    public DailyCurveResponse getDailyCurves(
            @PathVariable("deviceNo") String deviceNo,
            @RequestParam("date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.findByDay(deviceNo, date);
    }
}
