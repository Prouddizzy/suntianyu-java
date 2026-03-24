package com.suntianyu.stm32smartdisinfectorjava.service;

import com.suntianyu.stm32smartdisinfectorjava.common.BusinessException;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.DeviceCommand;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.DeviceCommandAck;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.PauseRequest;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.RuntimeStatus;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.StartTaskRequest;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.Thresholds;
import com.suntianyu.stm32smartdisinfectorjava.model.enums.DisinfectorMode;
import com.suntianyu.stm32smartdisinfectorjava.repository.RedisStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final RedisStateRepository redisRepository;
    private final CommandService commandService;
    private final DeviceStatusStreamService deviceStatusStreamService;

    private static final String DEFAULT_DEVICE_ID = "STM-001";
    private static final int COMMAND_TIMEOUT_MS = 4000;
    private static final int DEFAULT_DURATION_MINUTES = 20;
    private static final double DEFAULT_TEMP_LOW = 18.0;
    private static final double DEFAULT_TEMP_HIGH = 34.0;
    private static final double DEFAULT_HUMIDITY_LOW = 45.0;
    private static final double DEFAULT_HUMIDITY_HIGH = 65.0;
    private static final AtomicInteger CMD_SEQ = new AtomicInteger(0);

    public RuntimeStatus getRuntimeStatus() {
        RuntimeStatus status = redisRepository.getStatus(DEFAULT_DEVICE_ID);
        if (status == null) {
            status = new RuntimeStatus();
        }
        return ensureStatusDefaults(status);
    }

    public Thresholds getConfig() {
        Thresholds config = redisRepository.getConfig(DEFAULT_DEVICE_ID);
        if (config == null) {
            config = defaultThresholds();
        }
        return config;
    }

    public Thresholds updateThresholds(Thresholds thresholds) {
        validateThresholds(thresholds);

        DeviceCommand cmd = newCommand("thr");
        applyThresholdsToCommand(cmd, thresholds);

        try {
            DeviceCommandAck ack = commandService.sendCommand(cmd, COMMAND_TIMEOUT_MS);
            if (!ack.isOk()) {
                throw new BusinessException(ack.getCode() != 0 ? ack.getCode() : 2002, ack.getMessage());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Failed to update thresholds", e);
            throw new BusinessException(2002, "Communication failed: " + e.getMessage());
        }

        redisRepository.saveConfig(DEFAULT_DEVICE_ID, thresholds);
        RuntimeStatus current = getRuntimeStatus();
        applyThresholdsToStatus(current, thresholds);
        current.setUpdatedAt(LocalDateTime.now());
        redisRepository.saveStatus(DEFAULT_DEVICE_ID, current);
        deviceStatusStreamService.publish(current);
        return thresholds;
    }

    public RuntimeStatus startTask(StartTaskRequest request) {
        RuntimeStatus current = getRuntimeStatus();
        if (current.isDoorOpen()) {
            throw new BusinessException(1002, "Door is open");
        }
        if (current.isMachineRunning()) {
            throw new BusinessException(1003, "Device already running");
        }
        if (request == null || request.getMode() == null) {
            throw new BusinessException(1001, "Mode is required");
        }

        Integer duration = normalizeDuration(request);
        Thresholds effectiveThresholds = request.getThresholds() != null ? request.getThresholds() : getConfig();
        validateThresholds(effectiveThresholds);

        DeviceCommand cmd = newCommand("start");
        cmd.setMode(request.getMode().toDeviceKey());
        cmd.setDuration(duration);
        applyThresholdsToCommand(cmd, effectiveThresholds);

        try {
            DeviceCommandAck ack = commandService.sendCommand(cmd, COMMAND_TIMEOUT_MS);
            if (!ack.isOk()) {
                throw new BusinessException(ack.getCode() != 0 ? ack.getCode() : 2002, ack.getMessage());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Failed to start task", e);
            throw new BusinessException(2002, "Communication failed: " + e.getMessage());
        }

        current.setMachineRunning(true);
        current.setPaused(false);
        current.setDoorOpen(false);
        current.setSelectedMode(request.getMode());
        current.setSystemStatus("running");
        current.setDuration(duration);
        current.setRemainingSeconds(duration != null && duration > 0 ? duration * 60 : 0);
        applyActuatorStateForMode(current, request.getMode(), false);
        applyThresholdsToStatus(current, effectiveThresholds);
        current.setUpdatedAt(LocalDateTime.now());

        redisRepository.saveConfig(DEFAULT_DEVICE_ID, effectiveThresholds);
        redisRepository.saveStatus(DEFAULT_DEVICE_ID, current);
        deviceStatusStreamService.publish(current);
        return current;
    }

    public RuntimeStatus pauseTask(PauseRequest request) {
        RuntimeStatus current = getRuntimeStatus();
        if (!current.isMachineRunning()) {
            throw new BusinessException(1004, "Device is idle");
        }

        String action = normalizePauseAction(request);
        boolean isPause = "pause".equals(action);
        if (isPause && current.isPaused()) {
            throw new BusinessException(1006, "Already paused");
        }
        if (!isPause && !current.isPaused()) {
            throw new BusinessException(1006, "Already running");
        }

        DeviceCommand cmd = newCommand(action);
        try {
            DeviceCommandAck ack = commandService.sendCommand(cmd, COMMAND_TIMEOUT_MS);
            if (!ack.isOk()) {
                throw new BusinessException(ack.getCode() != 0 ? ack.getCode() : 2002, ack.getMessage());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BusinessException(2002, "Communication failed: " + e.getMessage());
        }

        current.setPaused(isPause);
        current.setSystemStatus(isPause ? "paused" : "running");
        if (isPause) {
            current.setHeaterOn(false);
            current.setDisinfectionOn(false);
            current.setFanOn(false);
        }
        current.setUpdatedAt(LocalDateTime.now());
        redisRepository.saveStatus(DEFAULT_DEVICE_ID, current);
        deviceStatusStreamService.publish(current);
        return current;
    }

    public RuntimeStatus stopTask() {
        RuntimeStatus current = getRuntimeStatus();
        if (!current.isMachineRunning()) {
            throw new BusinessException(1004, "Device is idle");
        }

        DeviceCommand cmd = newCommand("stop");
        try {
            DeviceCommandAck ack = commandService.sendCommand(cmd, COMMAND_TIMEOUT_MS);
            if (!ack.isOk()) {
                throw new BusinessException(ack.getCode() != 0 ? ack.getCode() : 2002, ack.getMessage());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BusinessException(2002, "Communication failed: " + e.getMessage());
        }

        current.setMachineRunning(false);
        current.setPaused(false);
        current.setSystemStatus("idle");
        current.setRemainingSeconds(0);
        current.setHeaterOn(false);
        current.setDisinfectionOn(false);
        current.setFanOn(false);
        current.setUpdatedAt(LocalDateTime.now());
        redisRepository.saveStatus(DEFAULT_DEVICE_ID, current);
        deviceStatusStreamService.publish(current);
        return current;
    }

    private RuntimeStatus ensureStatusDefaults(RuntimeStatus status) {
        Thresholds config = getConfig();
        status.setDeviceId(DEFAULT_DEVICE_ID);
        status.setDeviceOnline(redisRepository.isOnline(DEFAULT_DEVICE_ID));
        if (status.getLastSeenTs() == null && status.isDeviceOnline()) {
            status.setLastSeenTs(System.currentTimeMillis());
        }
        if (status.getSelectedMode() == null) {
            status.setSelectedMode(DisinfectorMode.SMART);
        }
        if (status.getDuration() == null) {
            status.setDuration(DEFAULT_DURATION_MINUTES);
        }
        if (status.getRemainingSeconds() == null) {
            status.setRemainingSeconds(0);
        }
        if (status.getTemperature() == null) {
            status.setTemperature(25.0);
        }
        if (status.getHumidity() == null) {
            status.setHumidity(50.0);
        }
        if (status.getTempLow() == null) {
            status.setTempLow(config.getTempLow());
        }
        if (status.getTempHigh() == null) {
            status.setTempHigh(config.getTempHigh());
        }
        if (status.getHumidityLow() == null) {
            status.setHumidityLow(config.getHumidityLow());
        }
        if (status.getHumidityHigh() == null) {
            status.setHumidityHigh(config.getHumidityHigh());
        }
        if (status.getSystemStatus() == null || status.getSystemStatus().isBlank()) {
            if (status.isPaused()) {
                status.setSystemStatus("paused");
            } else if (status.isMachineRunning()) {
                status.setSystemStatus("running");
            } else {
                status.setSystemStatus("idle");
            }
        }
        if (status.getUpdatedAt() == null) {
            status.setUpdatedAt(LocalDateTime.now());
        }
        return status;
    }

    private Thresholds defaultThresholds() {
        Thresholds thresholds = new Thresholds();
        thresholds.setTempLow(DEFAULT_TEMP_LOW);
        thresholds.setTempHigh(DEFAULT_TEMP_HIGH);
        thresholds.setHumidityLow(DEFAULT_HUMIDITY_LOW);
        thresholds.setHumidityHigh(DEFAULT_HUMIDITY_HIGH);
        return thresholds;
    }

    private void validateThresholds(Thresholds thresholds) {
        if (thresholds == null) {
            throw new BusinessException(1005, "Thresholds are required");
        }
        if (thresholds.getTempLow() == null || thresholds.getTempHigh() == null
                || thresholds.getHumidityLow() == null || thresholds.getHumidityHigh() == null) {
            throw new BusinessException(1005, "Threshold range incomplete");
        }
        if (thresholds.getTempLow() >= thresholds.getTempHigh()
                || thresholds.getHumidityLow() >= thresholds.getHumidityHigh()) {
            throw new BusinessException(1005, "Threshold range illegal");
        }
    }

    private Integer normalizeDuration(StartTaskRequest request) {
        if (request.getMode() == DisinfectorMode.SMART) {
            return 0;
        }
        if (request.getDuration() == null || request.getDuration() <= 0) {
            throw new BusinessException(1001, "Duration is required");
        }
        return request.getDuration();
    }

    private String normalizePauseAction(PauseRequest request) {
        if (request == null || request.getAction() == null || request.getAction().isBlank()) {
            return "pause";
        }
        String action = request.getAction().trim().toLowerCase();
        if (!"pause".equals(action) && !"resume".equals(action)) {
            throw new BusinessException(1001, "Invalid pause action");
        }
        return action;
    }

    private DeviceCommand newCommand(String action) {
        DeviceCommand cmd = new DeviceCommand();
        cmd.setType("cmd");
        cmd.setCmdId(nextCmdId());
        cmd.setDeviceId(DEFAULT_DEVICE_ID);
        cmd.setAction(action);
        return cmd;
    }

    private String nextCmdId() {
        int next = CMD_SEQ.updateAndGet(value -> (value + 1) & 0xFFFF);
        return String.format("C%03X", next & 0xFFF);
    }

    private void applyThresholdsToCommand(DeviceCommand cmd, Thresholds thresholds) {
        if (thresholds == null) {
            return;
        }
        cmd.setTempLow(thresholds.getTempLow());
        cmd.setTempHigh(thresholds.getTempHigh());
        cmd.setHumidityLow(thresholds.getHumidityLow());
        cmd.setHumidityHigh(thresholds.getHumidityHigh());
    }

    private void applyThresholdsToStatus(RuntimeStatus status, Thresholds thresholds) {
        if (thresholds == null) {
            return;
        }
        status.setTempLow(thresholds.getTempLow());
        status.setTempHigh(thresholds.getTempHigh());
        status.setHumidityLow(thresholds.getHumidityLow());
        status.setHumidityHigh(thresholds.getHumidityHigh());
    }

    private void applyActuatorStateForMode(RuntimeStatus status, DisinfectorMode mode, boolean paused) {
        if (paused) {
            status.setHeaterOn(false);
            status.setDisinfectionOn(false);
            status.setFanOn(false);
            return;
        }

        status.setHeaterOn(mode == DisinfectorMode.HEATING);
        status.setDisinfectionOn(mode == DisinfectorMode.DISINFECTION);
        status.setFanOn(mode == DisinfectorMode.FAN);
    }
}
