package com.suntianyu.stm32smartdisinfectorjava.controller;

import com.suntianyu.stm32smartdisinfectorjava.common.Result;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.*;
import com.suntianyu.stm32smartdisinfectorjava.service.DeviceService;
import com.suntianyu.stm32smartdisinfectorjava.service.DeviceStatusStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/disinfector")
@RequiredArgsConstructor
@CrossOrigin //Allow cross-origin for frontend dev
public class DisinfectorController {

    private final DeviceService deviceService;
    private final DeviceStatusStreamService deviceStatusStreamService;

    @GetMapping("/runtime-status")
    public Result<RuntimeStatus> getRuntimeStatus(
            @RequestParam(value = "deviceId", required = false) String deviceId) {
        return Result.success(deviceService.getRuntimeStatus(deviceId));
    }

    @GetMapping("/runtime-status/recent")
    public Result<List<RuntimeStatus>> getRecentRuntimeStatuses(
            @RequestParam(value = "deviceId", required = false) String deviceId,
            @RequestParam(value = "limit", required = false, defaultValue = "10") Integer limit) {
        return Result.success(deviceService.getRecentRuntimeStatuses(deviceId, limit));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamStatus(@RequestParam(value = "deviceId", required = false) String deviceId) {
        SseEmitter emitter = deviceStatusStreamService.subscribe(deviceId);
        deviceStatusStreamService.sendSnapshot(emitter, deviceService.getRuntimeStatus(deviceId));
        return emitter;
    }

    @GetMapping("/config")
    public Result<Thresholds> getConfig(
            @RequestParam(value = "deviceId", required = false) String deviceId) {
        return Result.success(deviceService.getConfig(deviceId));
    }

    @PutMapping("/thresholds")
    public Result<Thresholds> updateThresholds(
            @RequestParam(value = "deviceId", required = false) String deviceId,
            @RequestBody Thresholds thresholds) {
        return Result.success(deviceService.updateThresholds(deviceId, thresholds));
    }

    @PutMapping("/wifi-config")
    public Result<WifiConfigResponse> updateWifiConfig(
            @RequestParam(value = "deviceId", required = false) String deviceId,
            @RequestBody WifiConfigRequest request) {
        return Result.success(deviceService.updateWifiConfig(deviceId, request));
    }

    @PostMapping("/tasks/start")
    public Result<RuntimeStatus> startTask(
            @RequestParam(value = "deviceId", required = false) String deviceId,
            @RequestBody StartTaskRequest request) {
        return Result.success(deviceService.startTask(deviceId, request));
    }

    @PostMapping("/tasks/pause")
    public Result<RuntimeStatus> pauseTask(
            @RequestParam(value = "deviceId", required = false) String deviceId,
            @RequestBody PauseRequest request) {
        return Result.success(deviceService.pauseTask(deviceId, request));
    }

    @PostMapping("/tasks/stop")
    public Result<RuntimeStatus> stopTask(
            @RequestParam(value = "deviceId", required = false) String deviceId) {
        return Result.success(deviceService.stopTask(deviceId));
    }
}

