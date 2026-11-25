package com.example.ami.controller;

import com.example.ami.service.CurveSearchService;
import com.example.ami.dto.CurveSearchRequest;
import com.example.ami.dto.CurveSearchResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** 提供带条件的曲线分页检索接口。 */
@RestController
@RequestMapping("/curve-readings")
public class CurveSearchController {

    private final CurveSearchService service;

    public CurveSearchController(CurveSearchService service) {
        this.service = service;
    }

    /** 返回当前页及是否还有下一页。 */
    @PostMapping("/search")
    public CurveSearchResponse search(@Valid @RequestBody CurveSearchRequest request) {
        return service.search(request);
    }
}
