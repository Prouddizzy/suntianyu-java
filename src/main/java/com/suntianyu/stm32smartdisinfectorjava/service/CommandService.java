package com.suntianyu.stm32smartdisinfectorjava.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suntianyu.stm32smartdisinfectorjava.common.BusinessException;
import com.suntianyu.stm32smartdisinfectorjava.gateway.DeviceChannelRegistry;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.DeviceCommand;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.DeviceCommandAck;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommandService {

    private final DeviceChannelRegistry channelRegistry;
    private final ObjectMapper objectMapper;

    // Map cmdId -> Future
    private final Map<String, CompletableFuture<DeviceCommandAck>> pendingCommands = new ConcurrentHashMap<>();

    public DeviceCommandAck sendCommand(DeviceCommand cmd, long timeoutMs) {
        String deviceId = cmd.getDeviceId();
        String cmdId = cmd.getCmdId();
        Channel channel = channelRegistry.getChannel(deviceId);

        // Pre-send validation logging
        if (channel == null) {
            log.error("SendCommand failed: Channel not found for device {}", deviceId);
            throw new BusinessException(2101, "Device offline or channel not found: " + deviceId);
        }

        log.info("Preparing to send cmdId: {} to device: {}. Channel: [id: {}, remote: {}, active: {}, writable: {}]",
                cmdId, deviceId, channel.id(), channel.remoteAddress(), channel.isActive(), channel.isWritable());

        if (!channel.isActive()) {
            throw new BusinessException(2101, "Device channel inactive: " + deviceId);
        }

        CompletableFuture<DeviceCommandAck> future = new CompletableFuture<>();
        pendingCommands.put(cmdId, future);

        try {
            String json = objectMapper.writeValueAsString(cmd);
            String payload = json + "\n"; // Ensure delimiter
            log.info("Sending command payload. cmdId: {}, payload: {}", cmdId, json);

            // Write with listener to confirm network send
            channel.writeAndFlush(payload).addListener((ChannelFutureListener) f -> {
                if (f.isSuccess()) {
                    log.info("WriteAndFlush SUCCESS for cmdId: {}", cmdId);
                } else {
                    log.error("WriteAndFlush FAILED for cmdId: {}", cmdId, f.cause());
                    // Fail the future fast if write fails
                    CompletableFuture<DeviceCommandAck> p = pendingCommands.remove(cmdId);
                    if (p != null) {
                        p.completeExceptionally(new BusinessException(2101, "Network write failed"));
                    }
                }
            });

            // Wait for ACK
            try {
                DeviceCommandAck ack = future.get(timeoutMs, TimeUnit.MILLISECONDS);
                log.info("Command completed successfully. cmdId: {}, result: {}", cmdId, ack.isOk());
                return ack;
            } catch (TimeoutException e) {
                log.warn("Command TIMEOUT for cmdId: {} after {}ms", cmdId, timeoutMs);
                throw new BusinessException(2101, "Command timeout");
            } catch (Exception e) {
                if (e.getCause() instanceof BusinessException) {
                    throw (BusinessException) e.getCause();
                }
                throw new BusinessException(2101, "Command execution failed: " + e.getMessage());
            }

        } catch (JsonProcessingException e) {
            pendingCommands.remove(cmdId);
            throw new RuntimeException("Error serializing command", e);
        } finally {
            // Ensure cleanup
            pendingCommands.remove(cmdId);
        }
    }

    public void handleAck(DeviceCommandAck ack) {
        if (ack.getMessage() == null || ack.getMessage().isBlank()) {
            ack.setMessage(resolveAckMessage(ack.getCode()));
        }

        String cmdId = ack.getCmdId();
        CompletableFuture<DeviceCommandAck> future = pendingCommands.remove(cmdId);

        if (future != null) {
            log.info("Matching pending future found for cmdId: {}. Completing.", cmdId);
            future.complete(ack);
        } else {
            log.warn("Received ACK for unknown or timed out cmdId: {}. Pending map size: {}", cmdId, pendingCommands.size());
        }
    }

    private String resolveAckMessage(int code) {
        return switch (code) {
            case 0 -> "ok";
            case 1001 -> "参数非法";
            case 1002 -> "柜门打开，禁止启动";
            case 1004 -> "当前空闲，无法暂停/停止";
            case 1006 -> "暂停/继续状态非法";
            case 3001 -> "执行器控制失败";
            default -> "未知错误";
        };
    }
}
