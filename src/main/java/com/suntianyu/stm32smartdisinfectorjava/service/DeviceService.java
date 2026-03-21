package com.suntianyu.stm32smartdisinfectorjava.service;

import com.suntianyu.stm32smartdisinfectorjava.common.BusinessException;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.*;
import com.suntianyu.stm32smartdisinfectorjava.model.enums.DisinfectorMode;
import com.suntianyu.stm32smartdisinfectorjava.repository.RedisStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final RedisStateRepository redisRepository;
    private final CommandService commandService;

    // Hardcoded for single device project, or could be passed from frontend
    private static final String DEFAULT_DEVICE_ID = "cabinet-001";

    public RuntimeStatus getRuntimeStatus() {
        RuntimeStatus status = redisRepository.getStatus(DEFAULT_DEVICE_ID);
        if (status == null) {
            // Return a default empty status if not connected yet
            status = new RuntimeStatus();
            status.setMachineRunning(false);
            status.setPaused(false);
            status.setDoorOpen(false);
            status.setSelectedMode(DisinfectorMode.SMART);
            status.setDuration(20);
            status.setRemainingSeconds(0);
            status.setUpdatedAt(LocalDateTime.now());
            // mock values
            status.setTemperature(25.0);
            status.setHumidity(50.0);
            // Default threshold display
            status.setTempLow(24.0);
            status.setTempHigh(34.0);
            status.setHumidityLow(45.0);
            status.setHumidityHigh(65.0);
        }
        return status;
    }

    public Thresholds getConfig() {
        Thresholds config = redisRepository.getConfig(DEFAULT_DEVICE_ID);
        if (config == null) {
            config = new Thresholds();
            config.setTempLow(24.0);
            config.setTempHigh(34.0);
            config.setHumidityLow(45.0);
            config.setHumidityHigh(65.0);
        }
        return config;
    }

    public Thresholds updateThresholds(Thresholds thresholds) {
        // Validate
        if (thresholds.getTempLow() >= thresholds.getTempHigh() ||
            thresholds.getHumidityLow() >= thresholds.getHumidityHigh()) {
            throw new BusinessException(1005, "Threshold range illegal");
        }

        // Save to Redis
        redisRepository.saveConfig(DEFAULT_DEVICE_ID, thresholds);

        // Spec says we just update configs.
        // If we wanted to push to device on the fly, we would do it here.
        // For now, we assume standard behavior: config is just config.

        return thresholds;
    }

    public RuntimeStatus startTask(StartTaskRequest request) {
        // 1. Pre-checks
        RuntimeStatus current = getRuntimeStatus();
        if (current.isDoorOpen()) {
            throw new BusinessException(1002, "Door is open");
        }
        if (current.isMachineRunning()) {
            throw new BusinessException(1003, "Device already running");
        }
        // Check online status if enforced
        // if (!redisRepository.isOnline(DEFAULT_DEVICE_ID)) {
        //    throw new BusinessException(2001, "Device offline");
        // }

        // 2. Build Command
        DeviceCommand cmd = new DeviceCommand();
        cmd.setType("control_cmd");
        cmd.setCmdId(UUID.randomUUID().toString());
        cmd.setDeviceId(DEFAULT_DEVICE_ID);
        cmd.setAction("start");
        cmd.setMode(request.getMode());
        cmd.setDuration(request.getDuration());
        cmd.setThresholds(request.getThresholds());

        // 3. Send & Wait for ACK
        try {
            DeviceCommandAck ack = commandService.sendCommand(cmd, 3000);
            if (!ack.isOk()) {
                throw new BusinessException(ack.getCode() != 0 ? ack.getCode() : 2002, "Device refused start: " + ack.getMessage());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
             log.error("Failed to start", e);
             throw new BusinessException(2002, "Communication failed: " + e.getMessage());
        }

        // 4. Update local state immediately (optimistic)
        current.setMachineRunning(true);
        current.setSelectedMode(request.getMode());
        current.setDuration(request.getDuration());
        if (request.getDuration() != null) {
            current.setRemainingSeconds(request.getDuration() * 60);
        } else {
            current.setRemainingSeconds(0);
        }

        // Also update thresholds in runtime status locally
        if (request.getThresholds() != null) {
            current.setTempLow(request.getThresholds().getTempLow());
            current.setTempHigh(request.getThresholds().getTempHigh());
            current.setHumidityLow(request.getThresholds().getHumidityLow());
            current.setHumidityHigh(request.getThresholds().getHumidityHigh());
        }
        redisRepository.saveStatus(DEFAULT_DEVICE_ID, current);

        return current;
    }

    public RuntimeStatus pauseTask(PauseRequest request) {
        RuntimeStatus current = getRuntimeStatus();
        if (!current.isMachineRunning()) {
            throw new BusinessException(1004, "Device is idle");
        }

        boolean isPause = "pause".equalsIgnoreCase(request.getAction());
        if (isPause && current.isPaused()) throw new BusinessException(1006, "Already paused");
        if (!isPause && !current.isPaused()) throw new BusinessException(1006, "Already running");

        DeviceCommand cmd = new DeviceCommand();
        cmd.setType("control_cmd");
        cmd.setCmdId(UUID.randomUUID().toString());
        cmd.setDeviceId(DEFAULT_DEVICE_ID);
        cmd.setAction(request.getAction()); // pause or resume

        try {
            DeviceCommandAck ack = commandService.sendCommand(cmd, 3000);
            if (!ack.isOk()) throw new BusinessException(ack.getCode() != 0 ? ack.getCode() : 2002, "Device refused " + request.getAction());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
             throw new BusinessException(2002, "Communication failed");
        }

        current.setPaused(isPause);
        redisRepository.saveStatus(DEFAULT_DEVICE_ID, current);
        return current;
    }

    public RuntimeStatus stopTask() {
        RuntimeStatus current = getRuntimeStatus();
        if (!current.isMachineRunning()) {
            throw new BusinessException(1004, "Device is idle");
        }

        DeviceCommand cmd = new DeviceCommand();
        cmd.setType("control_cmd");
        cmd.setCmdId(UUID.randomUUID().toString());
        cmd.setDeviceId(DEFAULT_DEVICE_ID);
        cmd.setAction("stop");

        try {
            commandService.sendCommand(cmd, 3000);
        } catch (Exception e) {
            // Even if ack fails, we might still want to force stop locally?
            // Better to throw error so user knows it might not have stopped on device.
            throw new BusinessException(2002, "Communication failed");
        }

        current.setMachineRunning(false);
        current.setPaused(false);
        redisRepository.saveStatus(DEFAULT_DEVICE_ID, current);
        return current;
    }
}
