package com.suntianyu.stm32smartdisinfectorjava.controller;

import com.suntianyu.stm32smartdisinfectorjava.common.Result;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.*;
import com.suntianyu.stm32smartdisinfectorjava.service.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/disinfector")
@RequiredArgsConstructor
@CrossOrigin //Allow cross-origin for frontend dev
public class DisinfectorController {

    private final DeviceService deviceService;

    @GetMapping("/runtime-status")
    public Result<RuntimeStatus> getRuntimeStatus() {
        return Result.success(deviceService.getRuntimeStatus());
    }

    @GetMapping("/config")
    public Result<Thresholds> getConfig() {
        return Result.success(deviceService.getConfig());
    }

    @PutMapping("/thresholds")
    public Result<Thresholds> updateThresholds(@RequestBody Thresholds thresholds) {
        return Result.success(deviceService.updateThresholds(thresholds));
    }

    @PostMapping("/tasks/start")
    public Result<RuntimeStatus> startTask(@RequestBody StartTaskRequest request) {
        return Result.success(deviceService.startTask(request));
    }

    @PostMapping("/tasks/pause")
    public Result<RuntimeStatus> pauseTask(@RequestBody PauseRequest request) {
        return Result.success(deviceService.pauseTask(request));
    }

    @PostMapping("/tasks/stop")
    public Result<RuntimeStatus> stopTask() {
        return Result.success(deviceService.stopTask());
    }
}

