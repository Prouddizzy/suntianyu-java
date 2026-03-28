package com.suntianyu.stm32smartdisinfectorjava.service;

import com.suntianyu.stm32smartdisinfectorjava.common.BusinessException;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.DeviceCommand;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.DeviceCommandAck;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.PauseRequest;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.RuntimeStatus;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.StartTaskRequest;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.Thresholds;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.WifiConfigRequest;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.WifiConfigResponse;
import com.suntianyu.stm32smartdisinfectorjava.model.enums.DisinfectorMode;
import com.suntianyu.stm32smartdisinfectorjava.repository.InMemoryStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final InMemoryStateRepository stateRepository;
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
        return getRuntimeStatus(null);
    }

    public RuntimeStatus getRuntimeStatus(String deviceId) {
        String resolvedDeviceId = resolveDeviceId(deviceId);
        RuntimeStatus status = stateRepository.getStatus(resolvedDeviceId);
        if (status == null) {
            status = new RuntimeStatus();
        }
        return ensureStatusDefaults(resolvedDeviceId, status);
    }

    public List<RuntimeStatus> getRecentRuntimeStatuses(Integer limit) {
        return getRecentRuntimeStatuses(null, limit);
    }

    public List<RuntimeStatus> getRecentRuntimeStatuses(String deviceId, Integer limit) {
        String resolvedDeviceId = resolveDeviceId(deviceId);
        int resolvedLimit = limit == null ? 10 : limit;
        return stateRepository.getRecentStatuses(resolvedDeviceId, resolvedLimit)
                .stream()
                .map(status -> ensureStatusDefaults(resolvedDeviceId, status))
                .toList();
    }

    public Thresholds getConfig() {
        return getConfig(null);
    }

    public Thresholds getConfig(String deviceId) {
        String resolvedDeviceId = resolveDeviceId(deviceId);
        Thresholds config = stateRepository.getConfig(resolvedDeviceId);
        if (config == null) {
            config = defaultThresholds();
        }
        return config;
    }

    public Thresholds updateThresholds(Thresholds thresholds) {
        return updateThresholds(null, thresholds);
    }

    public Thresholds updateThresholds(String deviceId, Thresholds thresholds) {
        String resolvedDeviceId = resolveDeviceId(deviceId);
        validateThresholds(thresholds);

        DeviceCommand cmd = newCommand(resolvedDeviceId, "thr");
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

        stateRepository.saveConfig(resolvedDeviceId, thresholds);
        RuntimeStatus current = getRuntimeStatus(resolvedDeviceId);
        applyThresholdsToStatus(current, thresholds);
        current.setUpdatedAt(LocalDateTime.now());
        stateRepository.saveStatus(resolvedDeviceId, current);
        deviceStatusStreamService.publish(current);
        return thresholds;
    }

    public RuntimeStatus startTask(StartTaskRequest request) {
        return startTask(null, request);
    }

    public RuntimeStatus startTask(String deviceId, StartTaskRequest request) {
        String resolvedDeviceId = resolveDeviceId(deviceId);
        RuntimeStatus current = getRuntimeStatus(resolvedDeviceId);
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
        Thresholds effectiveThresholds = request.getThresholds() != null
                ? request.getThresholds()
                : getConfig(resolvedDeviceId);
        validateThresholds(effectiveThresholds);

        DeviceCommand cmd = newCommand(resolvedDeviceId, "start");
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

        stateRepository.saveConfig(resolvedDeviceId, effectiveThresholds);
        stateRepository.saveStatus(resolvedDeviceId, current);
        deviceStatusStreamService.publish(current);
        return current;
    }

    public RuntimeStatus pauseTask(PauseRequest request) {
        return pauseTask(null, request);
    }

    public RuntimeStatus pauseTask(String deviceId, PauseRequest request) {
        String resolvedDeviceId = resolveDeviceId(deviceId);
        RuntimeStatus current = getRuntimeStatus(resolvedDeviceId);
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

        DeviceCommand cmd = newCommand(resolvedDeviceId, action);
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
        stateRepository.saveStatus(resolvedDeviceId, current);
        deviceStatusStreamService.publish(current);
        return current;
    }

    public RuntimeStatus stopTask() {
        return stopTask(null);
    }

    public RuntimeStatus stopTask(String deviceId) {
        String resolvedDeviceId = resolveDeviceId(deviceId);
        RuntimeStatus current = getRuntimeStatus(resolvedDeviceId);
        if (!current.isMachineRunning()) {
            throw new BusinessException(1004, "Device is idle");
        }

        DeviceCommand cmd = newCommand(resolvedDeviceId, "stop");
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
        stateRepository.saveStatus(resolvedDeviceId, current);
        deviceStatusStreamService.publish(current);
        return current;
    }

    public WifiConfigResponse updateWifiConfig(String deviceId, WifiConfigRequest request) {
        String resolvedDeviceId = resolveDeviceId(deviceId);
        WifiConfigRequest sanitizedRequest = sanitizeWifiConfig(request);

        DeviceCommand cmd = newCommand(resolvedDeviceId, "wifi");
        cmd.setWifiSsid(sanitizedRequest.getSsid());
        cmd.setWifiPassword(sanitizedRequest.getPassword());

        try {
            DeviceCommandAck ack = commandService.sendCommand(cmd, COMMAND_TIMEOUT_MS);
            if (!ack.isOk()) {
                throw new BusinessException(ack.getCode() != 0 ? ack.getCode() : 2002, ack.getMessage());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Failed to update wifi config for device {}", resolvedDeviceId, e);
            throw new BusinessException(2002, "Communication failed: " + e.getMessage());
        }

        return new WifiConfigResponse(resolvedDeviceId, sanitizedRequest.getSsid(), true);
    }

    private RuntimeStatus ensureStatusDefaults(String deviceId, RuntimeStatus status) {
        Thresholds config = getConfig(deviceId);
        status.setDeviceId(deviceId);
        status.setDeviceOnline(stateRepository.isOnline(deviceId));
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

    private DeviceCommand newCommand(String deviceId, String action) {
        DeviceCommand cmd = new DeviceCommand();
        cmd.setType("cmd");
        cmd.setCmdId(nextCmdId());
        cmd.setDeviceId(resolveDeviceId(deviceId));
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

    private WifiConfigRequest sanitizeWifiConfig(WifiConfigRequest request) {
        if (request == null) {
            throw new BusinessException(1001, "WiFi config is required");
        }

        String ssid = request.getSsid() == null ? "" : request.getSsid().trim();
        String password = request.getPassword() == null ? "" : request.getPassword().trim();

        if (ssid.isEmpty()) {
            throw new BusinessException(1001, "WiFi SSID is required");
        }
        if (ssid.length() > 32) {
            throw new BusinessException(1001, "WiFi SSID too long");
        }
        if (password.length() < 8 || password.length() > 64) {
            throw new BusinessException(1001, "WiFi password length invalid");
        }

        WifiConfigRequest sanitized = new WifiConfigRequest();
        sanitized.setSsid(ssid);
        sanitized.setPassword(password);
        return sanitized;
    }

    private String resolveDeviceId(String deviceId) {
        return deviceId == null || deviceId.isBlank() ? DEFAULT_DEVICE_ID : deviceId.trim();
    }
}
