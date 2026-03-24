package com.suntianyu.stm32smartdisinfectorjava.controller;

import com.suntianyu.stm32smartdisinfectorjava.common.Result;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.*;
import com.suntianyu.stm32smartdisinfectorjava.service.DeviceService;
import com.suntianyu.stm32smartdisinfectorjava.service.DeviceStatusStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/disinfector")
@RequiredArgsConstructor
@CrossOrigin //Allow cross-origin for frontend dev
public class DisinfectorController {

    private final DeviceService deviceService;
    private final DeviceStatusStreamService deviceStatusStreamService;

    @GetMapping("/runtime-status")
    public Result<RuntimeStatus> getRuntimeStatus() {
        return Result.success(deviceService.getRuntimeStatus());
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamStatus(@RequestParam(value = "deviceId", required = false) String deviceId) {
        SseEmitter emitter = deviceStatusStreamService.subscribe(deviceId);
        deviceStatusStreamService.sendSnapshot(emitter, deviceService.getRuntimeStatus());
        return emitter;
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

