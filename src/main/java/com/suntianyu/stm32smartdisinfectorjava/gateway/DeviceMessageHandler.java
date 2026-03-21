package com.suntianyu.stm32smartdisinfectorjava.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suntianyu.stm32smartdisinfectorjava.common.Result;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.DeviceCommandAck;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.DeviceStatusReport;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.RuntimeStatus;
import com.suntianyu.stm32smartdisinfectorjava.model.enums.DisinfectorMode;
import com.suntianyu.stm32smartdisinfectorjava.repository.RedisStateRepository;
import com.suntianyu.stm32smartdisinfectorjava.service.CommandService;
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

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("New connection: {}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("Client disconnected: {}", ctx.channel().remoteAddress());
        channelRegistry.removeChannel(ctx.channel());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
             IdleStateEvent event = (IdleStateEvent) evt;
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
        if (msg == null || msg.trim().isEmpty()) return;
        log.info("Received: {}", msg);

        try {
            JsonNode root = objectMapper.readTree(msg);
            if (!root.has("type")) {
                log.warn("Unknown message format (no type): {}", msg);
                return;
            }

            String type = root.get("type").asText();
            String deviceId = root.has("deviceId") ? root.get("deviceId").asText() : "unknown";

            // Register channel binding if not already done or changed
            if (!"unknown".equals(deviceId)) {
                channelRegistry.register(deviceId, ctx.channel());
                redisRepository.updateLastSeen(deviceId);
            }

            switch (type) {
                case "status_report":
                    handleStatusReport(msg, deviceId);
                    break;
                case "ack":
                    handleAck(msg);
                    break;
                default:
                    log.warn("Unknown message type: {}", type);
            }

        } catch (JsonProcessingException e) {
            log.error("Invalid JSON: {}", msg, e);
        }
    }

    private void handleStatusReport(String json, String deviceId) throws JsonProcessingException {
        DeviceStatusReport report = objectMapper.readValue(json, DeviceStatusReport.class);

        RuntimeStatus status = new RuntimeStatus();
        status.setMachineRunning(report.isMachineRunning());
        status.setPaused(report.isPaused());
        // Map string mode to Enum
        try {
             // Handle potential null or mismatch
            if (report.getMode() != null) {
                // Try direct match first (e.g. "智能模式")
                for (DisinfectorMode m : DisinfectorMode.values()) {
                    if (m.getValue().equals(report.getMode())) {
                        status.setSelectedMode(m);
                        break;
                    }
                }
                // If still null, maybe fallback or default
            } else {
                status.setSelectedMode(DisinfectorMode.SMART); // Default
            }
        } catch (Exception e) {
            log.warn("Error parsing mode: {}", report.getMode());
            status.setSelectedMode(DisinfectorMode.SMART);
        }

        status.setDuration(report.getDuration());
        status.setRemainingSeconds(report.getRemainingSeconds());
        status.setTemperature(report.getTemperature());
        status.setHumidity(report.getHumidity());
        status.setDoorOpen(report.isDoorOpen());
        status.setHeaterOn(report.isHeaterOn());
        status.setDisinfectionOn(report.isDisinfectionOn());
        status.setFanOn(report.isFanOn());

        status.setFaultCode(report.getFaultCode()); // Map fault code

        status.setUpdatedAt(LocalDateTime.now());

        // Also update thresholds if reported
        status.setTempLow(report.getTempLow());
        status.setTempHigh(report.getTempHigh());
        status.setHumidityLow(report.getHumidityLow());
        status.setHumidityHigh(report.getHumidityHigh());

        redisRepository.saveStatus(deviceId, status);
    }

    private void handleAck(String json) throws JsonProcessingException {
        DeviceCommandAck ack = objectMapper.readValue(json, DeviceCommandAck.class);
        commandService.handleAck(ack);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Netty exception", cause);
        ctx.close();
    }
}
