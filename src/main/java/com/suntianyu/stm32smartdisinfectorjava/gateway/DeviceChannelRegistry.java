package com.suntianyu.stm32smartdisinfectorjava.gateway;

import io.netty.channel.Channel;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DeviceChannelRegistry {
    private final Map<String, Channel> deviceChannels = new ConcurrentHashMap<>();

    public void register(String deviceId, Channel channel) {
        deviceChannels.put(deviceId, channel);
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
}

