package com.suntianyu.stm32smartdisinfectorjava.gateway;

import io.netty.channel.Channel;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DeviceChannelRegistry {
    private final Map<String, Channel> deviceChannels = new ConcurrentHashMap<>();

    public void register(String deviceId, Channel channel) {
        Channel previous = deviceChannels.put(deviceId, channel);
        if (previous != null && previous != channel && previous.isActive()) {
            previous.close();
        }
    }

    public void unregister(String deviceId) {
        deviceChannels.remove(deviceId);
    }

    public void removeChannel(Channel channel) {
        deviceChannels.values().remove(channel);
    }

    public Channel getChannel(String deviceId) {
        return deviceChannels.get(deviceId);
    }

    public Optional<String> findDeviceId(Channel channel) {
        return deviceChannels.entrySet()
                .stream()
                .filter(entry -> entry.getValue() == channel)
                .map(Map.Entry::getKey)
                .findFirst();
    }
}

