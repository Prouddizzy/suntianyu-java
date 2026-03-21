package com.suntianyu.stm32smartdisinfectorjava.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suntianyu.stm32smartdisinfectorjava.gateway.DeviceChannelRegistry;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.DeviceCommand;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.DeviceCommandAck;
import io.netty.channel.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommandService {

    private final DeviceChannelRegistry channelRegistry;
    private final ObjectMapper objectMapper;

    // Map cmdId -> Future
    private final Map<String, CompletableFuture<DeviceCommandAck>> pendingCommands = new ConcurrentHashMap<>();

    public DeviceCommandAck sendCommand(DeviceCommand cmd, long timeoutMs) {
        Channel channel = channelRegistry.getChannel(cmd.getDeviceId());
        if (channel == null || !channel.isActive()) {
            throw new RuntimeException("Device offline: " + cmd.getDeviceId());
        }

        CompletableFuture<DeviceCommandAck> future = new CompletableFuture<>();
        pendingCommands.put(cmd.getCmdId(), future);

        try {
            String json = objectMapper.writeValueAsString(cmd);
            channel.writeAndFlush(json + "\n");

            log.info("Sent command: {}", json);

            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (JsonProcessingException e) {
            pendingCommands.remove(cmd.getCmdId());
            throw new RuntimeException("Error serializing command", e);
        } catch (java.util.concurrent.TimeoutException e) {
            pendingCommands.remove(cmd.getCmdId());
            throw new RuntimeException("Command timeout", e);
        } catch (Exception e) {
            pendingCommands.remove(cmd.getCmdId());
            throw new RuntimeException("Command execution failed", e);
        }
    }

    public void handleAck(DeviceCommandAck ack) {
        CompletableFuture<DeviceCommandAck> future = pendingCommands.remove(ack.getCmdId());
        if (future != null) {
            future.complete(ack);
        } else {
            log.warn("Received ACK for unknown or timed out cmdId: {}", ack.getCmdId());
        }
    }
}

