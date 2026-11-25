package com.example.ami.controller;

import com.example.ami.service.ReadingCorrectionService;
import com.example.ami.dto.Correction;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 接收已有采样点的读数补正。 */
@RestController
@RequestMapping("/devices")
public class ReadingCorrectionController {

    private final ReadingCorrectionService service;

    public ReadingCorrectionController(ReadingCorrectionService service) {
        this.service = service;
    }

    /** 更新已有采样点，设备或读数不存在时返回 404。 */
    @PatchMapping("/{deviceNo}/curve-readings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void correct(@PathVariable String deviceNo, @Valid @RequestBody Correction correction) {
        service.correct(deviceNo, correction);
    }
}
