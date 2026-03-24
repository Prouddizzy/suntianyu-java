package com.suntianyu.stm32smartdisinfectorjava.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.DeviceCommandAck;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.DeviceEventReport;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.DeviceStatusReport;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.RuntimeStatus;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.Thresholds;
import com.suntianyu.stm32smartdisinfectorjava.model.enums.DisinfectorMode;
import com.suntianyu.stm32smartdisinfectorjava.repository.RedisStateRepository;
import com.suntianyu.stm32smartdisinfectorjava.service.CommandService;
import com.suntianyu.stm32smartdisinfectorjava.service.DeviceStatusStreamService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@io.netty.channel.ChannelHandler.Sharable
@RequiredArgsConstructor
public class DeviceMessageHandler extends SimpleChannelInboundHandler<String> {

    private final ObjectMapper objectMapper;
    private final DeviceChannelRegistry channelRegistry;
    private final RedisStateRepository redisRepository;
    private final CommandService commandService;
    private final DeviceStatusStreamService deviceStatusStreamService;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("New connection: {}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("Client disconnected: {}", ctx.channel().remoteAddress());
        RuntimeStatus status = channelRegistry.findDeviceId(ctx.channel())
                .map(this::loadStatus)
                .orElse(null);
        if (status != null) {
            status.setDeviceOnline(false);
            status.setUpdatedAt(LocalDateTime.now());
            redisRepository.saveStatus(status.getDeviceId(), status);
            deviceStatusStreamService.publish(status);
        }
        channelRegistry.removeChannel(ctx.channel());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent event) {
            if (event.state() == IdleState.READER_IDLE) {
                log.warn("Device idle timeout, closing connection: {}", ctx.channel().remoteAddress());
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
        if (msg == null || msg.trim().isEmpty()) {
            return;
        }
        log.info("Received: {}", msg);

        try {
            JsonNode root = objectMapper.readTree(msg);
            String type = firstText(root, "t", "type");
            if (!hasText(type)) {
                log.warn("Unknown message format (no type): {}", msg);
                return;
            }

            String deviceId = firstText(root, "id", "deviceId");
            if (!hasText(deviceId)) {
                deviceId = "unknown";
            }

            if (!"unknown".equals(deviceId)) {
                channelRegistry.register(deviceId, ctx.channel());
                redisRepository.updateLastSeen(deviceId);
            }

            switch (type) {
                case "hello" -> handleHello(msg, deviceId);
                case "tele", "status_report" -> handleStatusReport(msg, deviceId);
                case "evt", "event_report" -> handleEventReport(msg, deviceId);
                case "ack" -> handleAck(msg);
                case "ping", "pong" -> {
                }
                default -> log.warn("Unknown message type: {}", type);
            }
        } catch (JsonProcessingException e) {
            log.error("Invalid JSON: {}", msg, e);
        }
    }

    private void handleHello(String json, String deviceId) throws JsonProcessingException {
        if ("unknown".equals(deviceId)) {
            return;
        }

        DeviceStatusReport report = objectMapper.readValue(json, DeviceStatusReport.class);
        RuntimeStatus status = loadStatus(deviceId);

        mergeSnapshot(
                status,
                report.getMode(),
                report.getSystemStatus(),
                report.getTemperature(),
                report.getHumidity(),
                report.getDoorOpen(),
                report.getMachineRunning(),
                report.getPaused(),
                report.getHeaterOn(),
                report.getDisinfectionOn(),
                report.getFanOn(),
                report.getDuration(),
                report.getRemainingSeconds(),
                report.getFaultCode(),
                report.getTempLow(),
                report.getTempHigh(),
                report.getHumidityLow(),
                report.getHumidityHigh(),
                report.getFwVersion()
        );

        Thresholds thresholds = buildThresholds(
                report.getTempLow(),
                report.getTempHigh(),
                report.getHumidityLow(),
                report.getHumidityHigh()
        );
        if (thresholds != null) {
            redisRepository.saveConfig(deviceId, thresholds);
        }

        redisRepository.saveStatus(deviceId, status);
        deviceStatusStreamService.publish(status);
    }

    private void handleStatusReport(String json, String deviceId) throws JsonProcessingException {
        DeviceStatusReport report = objectMapper.readValue(json, DeviceStatusReport.class);
        RuntimeStatus status = loadStatus(deviceId);

        mergeSnapshot(
                status,
                report.getMode(),
                report.getSystemStatus(),
                report.getTemperature(),
                report.getHumidity(),
                report.getDoorOpen(),
                report.getMachineRunning(),
                report.getPaused(),
                report.getHeaterOn(),
                report.getDisinfectionOn(),
                report.getFanOn(),
                report.getDuration(),
                report.getRemainingSeconds(),
                report.getFaultCode(),
                report.getTempLow(),
                report.getTempHigh(),
                report.getHumidityLow(),
                report.getHumidityHigh(),
                report.getFwVersion()
        );

        Thresholds thresholds = buildThresholds(
                report.getTempLow(),
                report.getTempHigh(),
                report.getHumidityLow(),
                report.getHumidityHigh()
        );
        if (thresholds != null) {
            redisRepository.saveConfig(deviceId, thresholds);
        }

        redisRepository.saveStatus(deviceId, status);
        deviceStatusStreamService.publish(status);
    }

    private void handleEventReport(String json, String deviceId) throws JsonProcessingException {
        DeviceEventReport report = objectMapper.readValue(json, DeviceEventReport.class);
        RuntimeStatus status = loadStatus(deviceId);

        mergeSnapshot(
                status,
                report.getMode(),
                report.getSystemStatus(),
                report.getTemperature(),
                report.getHumidity(),
                report.getDoorOpen(),
                report.getMachineRunning(),
                report.getPaused(),
                report.getHeaterOn(),
                report.getDisinfectionOn(),
                report.getFanOn(),
                report.getDuration(),
                report.getRemainingSeconds(),
                null,
                report.getTempLow(),
                report.getTempHigh(),
                report.getHumidityLow(),
                report.getHumidityHigh(),
                null
        );

        if (hasText(report.getEventCode())) {
            status.setLastEventCode(report.getEventCode());
            status.setLastEventMessage(resolveEventMessage(report.getEventCode()));
        }
        if (hasText(report.getMessage())) {
            status.setLastEventMessage(report.getMessage());
        }
        status.setLastEventSource(hasText(report.getEventSource()) ? report.getEventSource() : "device");
        status.setLastEventTs(report.getTs() != null ? report.getTs() : System.currentTimeMillis());

        Thresholds thresholds = buildThresholds(
                report.getTempLow(),
                report.getTempHigh(),
                report.getHumidityLow(),
                report.getHumidityHigh()
        );
        if (thresholds != null) {
            redisRepository.saveConfig(deviceId, thresholds);
        }

        redisRepository.saveStatus(deviceId, status);
        deviceStatusStreamService.publish(status);
    }

    private void handleAck(String json) throws JsonProcessingException {
        DeviceCommandAck ack = objectMapper.readValue(json, DeviceCommandAck.class);
        commandService.handleAck(ack);
    }

    private RuntimeStatus loadStatus(String deviceId) {
        RuntimeStatus status = redisRepository.getStatus(deviceId);
        if (status == null) {
            status = new RuntimeStatus();
            status.setSelectedMode(DisinfectorMode.SMART);
            status.setSystemStatus("idle");
        }
        status.setDeviceId(deviceId);
        status.setDeviceOnline(true);
        status.setLastSeenTs(System.currentTimeMillis());
        status.setUpdatedAt(LocalDateTime.now());
        return status;
    }

    private void mergeSnapshot(RuntimeStatus status,
                               String modeValue,
                               String systemStatus,
                               Double temperature,
                               Double humidity,
                               Boolean doorOpen,
                               Boolean machineRunning,
                               Boolean paused,
                               Boolean heaterOn,
                               Boolean disinfectionOn,
                               Boolean fanOn,
                               Integer duration,
                               Integer remainingSeconds,
                               Integer faultCode,
                               Double tempLow,
                               Double tempHigh,
                               Double humidityLow,
                               Double humidityHigh,
                               String fwVersion) {
        DisinfectorMode parsedMode = DisinfectorMode.fromDeviceValue(modeValue);
        if (parsedMode != null) {
            status.setSelectedMode(parsedMode);
        }

        String normalizedStatus = normalizeSystemStatus(systemStatus);
        if (hasText(normalizedStatus)) {
            status.setSystemStatus(normalizedStatus);
        }
        if (temperature != null) {
            status.setTemperature(temperature);
        }
        if (humidity != null) {
            status.setHumidity(humidity);
        }
        if (doorOpen != null) {
            status.setDoorOpen(doorOpen);
        }
        if (machineRunning != null) {
            status.setMachineRunning(machineRunning);
        }
        if (paused != null) {
            status.setPaused(paused);
        }
        if (heaterOn != null) {
            status.setHeaterOn(heaterOn);
        }
        if (disinfectionOn != null) {
            status.setDisinfectionOn(disinfectionOn);
        }
        if (fanOn != null) {
            status.setFanOn(fanOn);
        }
        if (duration != null) {
            status.setDuration(duration);
        }
        if (remainingSeconds != null) {
            status.setRemainingSeconds(remainingSeconds);
        }
        if (faultCode != null) {
            status.setFaultCode(faultCode);
        }
        if (tempLow != null) {
            status.setTempLow(tempLow);
        }
        if (tempHigh != null) {
            status.setTempHigh(tempHigh);
        }
        if (humidityLow != null) {
            status.setHumidityLow(humidityLow);
        }
        if (humidityHigh != null) {
            status.setHumidityHigh(humidityHigh);
        }
        if (hasText(fwVersion)) {
            status.setFwVersion(fwVersion);
        }
    }

    private Thresholds buildThresholds(Double tempLow,
                                       Double tempHigh,
                                       Double humidityLow,
                                       Double humidityHigh) {
        if (tempLow == null || tempHigh == null || humidityLow == null || humidityHigh == null) {
            return null;
        }

        Thresholds thresholds = new Thresholds();
        thresholds.setTempLow(tempLow);
        thresholds.setTempHigh(tempHigh);
        thresholds.setHumidityLow(humidityLow);
        thresholds.setHumidityHigh(humidityHigh);
        return thresholds;
    }

    private String firstText(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (root.hasNonNull(fieldName)) {
                return root.get(fieldName).asText();
            }
        }
        return null;
    }

    private String normalizeSystemStatus(String systemStatus) {
        if (!hasText(systemStatus)) {
            return systemStatus;
        }

        return switch (systemStatus.trim().toLowerCase()) {
            case "set", "setting" -> "setting";
            case "run", "running" -> "running";
            case "pause", "paused" -> "paused";
            case "stop", "stop_confirm" -> "stop_confirm";
            case "done" -> "done";
            case "idle" -> "idle";
            default -> systemStatus;
        };
    }

    private String resolveEventMessage(String eventCode) {
        if (!hasText(eventCode)) {
            return null;
        }

        return switch (eventCode) {
            case "mode", "mode_selected" -> "mode setting";
            case "block", "start_blocked" -> "start blocked";
            case "start", "task_started" -> "task started";
            case "pause", "task_paused" -> "task paused";
            case "resume", "task_resumed" -> "task resumed";
            case "stop", "task_stopped" -> "task stopped";
            case "done", "task_done" -> "task done";
            case "door_pause" -> "door opened, task paused";
            case "stage", "smart_stage_changed" -> "smart stage changed";
            case "thr", "thresholds_updated" -> "thresholds updated";
            case "cancel", "setting_canceled" -> "setting canceled";
            case "door_open" -> "door opened";
            case "door_close" -> "door closed";
            default -> eventCode;
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Netty exception", cause);
        ctx.close();
    }
}
