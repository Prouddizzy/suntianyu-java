package com.suntianyu.stm32smartdisinfectorjava.repository;

import com.suntianyu.stm32smartdisinfectorjava.model.dto.RuntimeStatus;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.Thresholds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryStateRepository {

    private final ConcurrentHashMap<String, DeviceCache> deviceCaches = new ConcurrentHashMap<>();
    private final long offlineTimeoutMs;
    private final int recentStatusLimit;

    public InMemoryStateRepository(
            @Value("${app.device.offline-timeout-ms:45000}") long offlineTimeoutMs,
            @Value("${app.cache.runtime-status-limit:10}") int recentStatusLimit) {
        this.offlineTimeoutMs = offlineTimeoutMs;
        this.recentStatusLimit = Math.max(1, recentStatusLimit);
    }

    public void saveStatus(String deviceId, RuntimeStatus status) {
        if (!hasText(deviceId) || status == null) {
            return;
        }

        DeviceCache cache = getDeviceCache(deviceId);
        RuntimeStatus snapshot = copyStatus(status);
        snapshot.setDeviceId(deviceId);

        synchronized (cache) {
            cache.latestStatus = snapshot;
            cache.recentStatuses.addFirst(copyStatus(snapshot));
            while (cache.recentStatuses.size() > recentStatusLimit) {
                cache.recentStatuses.removeLast();
            }
        }
    }

    public RuntimeStatus getStatus(String deviceId) {
        if (!hasText(deviceId)) {
            return null;
        }

        DeviceCache cache = deviceCaches.get(deviceId);
        if (cache == null) {
            return null;
        }

        synchronized (cache) {
            return copyStatus(cache.latestStatus);
        }
    }

    public List<RuntimeStatus> getRecentStatuses(String deviceId, int limit) {
        if (!hasText(deviceId)) {
            return List.of();
        }

        DeviceCache cache = deviceCaches.get(deviceId);
        if (cache == null) {
            return List.of();
        }

        int resolvedLimit = Math.max(1, Math.min(limit, recentStatusLimit));
        List<RuntimeStatus> snapshots = new ArrayList<>(resolvedLimit);
        synchronized (cache) {
            int count = 0;
            for (RuntimeStatus recentStatus : cache.recentStatuses) {
                if (count >= resolvedLimit) {
                    break;
                }
                snapshots.add(copyStatus(recentStatus));
                count++;
            }
        }
        return snapshots;
    }

    public void saveConfig(String deviceId, Thresholds thresholds) {
        if (!hasText(deviceId) || thresholds == null) {
            return;
        }

        DeviceCache cache = getDeviceCache(deviceId);
        synchronized (cache) {
            cache.config = copyThresholds(thresholds);
        }
    }

    public Thresholds getConfig(String deviceId) {
        if (!hasText(deviceId)) {
            return null;
        }

        DeviceCache cache = deviceCaches.get(deviceId);
        if (cache == null) {
            return null;
        }

        synchronized (cache) {
            return copyThresholds(cache.config);
        }
    }

    public void updateLastSeen(String deviceId) {
        if (!hasText(deviceId)) {
            return;
        }

        DeviceCache cache = getDeviceCache(deviceId);
        synchronized (cache) {
            cache.lastSeenTs = System.currentTimeMillis();
            cache.online = true;
        }
    }

    public void markOffline(String deviceId) {
        if (!hasText(deviceId)) {
            return;
        }

        DeviceCache cache = getDeviceCache(deviceId);
        synchronized (cache) {
            cache.online = false;
        }
    }

    public boolean isOnline(String deviceId) {
        if (!hasText(deviceId)) {
            return false;
        }

        DeviceCache cache = deviceCaches.get(deviceId);
        if (cache == null) {
            return false;
        }

        Long lastSeenTs;
        boolean online;
        synchronized (cache) {
            lastSeenTs = cache.lastSeenTs;
            online = cache.online;
        }
        return online && lastSeenTs != null && System.currentTimeMillis() - lastSeenTs < offlineTimeoutMs;
    }

    private DeviceCache getDeviceCache(String deviceId) {
        return deviceCaches.computeIfAbsent(deviceId, key -> new DeviceCache());
    }

    private RuntimeStatus copyStatus(RuntimeStatus source) {
        if (source == null) {
            return null;
        }

        RuntimeStatus target = new RuntimeStatus();
        target.setDeviceId(source.getDeviceId());
        target.setDeviceOnline(source.isDeviceOnline());
        target.setLastSeenTs(source.getLastSeenTs());
        target.setMachineRunning(source.isMachineRunning());
        target.setPaused(source.isPaused());
        target.setSelectedMode(source.getSelectedMode());
        target.setDuration(source.getDuration());
        target.setRemainingSeconds(source.getRemainingSeconds());
        target.setTemperature(source.getTemperature());
        target.setHumidity(source.getHumidity());
        target.setDoorOpen(source.isDoorOpen());
        target.setHeaterOn(source.isHeaterOn());
        target.setDisinfectionOn(source.isDisinfectionOn());
        target.setFanOn(source.isFanOn());
        target.setTempLow(source.getTempLow());
        target.setTempHigh(source.getTempHigh());
        target.setHumidityLow(source.getHumidityLow());
        target.setHumidityHigh(source.getHumidityHigh());
        target.setFaultCode(source.getFaultCode());
        target.setSystemStatus(source.getSystemStatus());
        target.setStage(source.getStage());
        target.setFwVersion(source.getFwVersion());
        target.setLastEventCode(source.getLastEventCode());
        target.setLastEventMessage(source.getLastEventMessage());
        target.setLastEventSource(source.getLastEventSource());
        target.setLastEventTs(source.getLastEventTs());
        target.setUpdatedAt(copyLocalDateTime(source.getUpdatedAt()));
        return target;
    }

    private Thresholds copyThresholds(Thresholds source) {
        if (source == null) {
            return null;
        }

        Thresholds target = new Thresholds();
        target.setTempLow(source.getTempLow());
        target.setTempHigh(source.getTempHigh());
        target.setHumidityLow(source.getHumidityLow());
        target.setHumidityHigh(source.getHumidityHigh());
        return target;
    }

    private LocalDateTime copyLocalDateTime(LocalDateTime source) {
        return source == null ? null : LocalDateTime.from(source);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class DeviceCache {
        private RuntimeStatus latestStatus;
        private Thresholds config;
        private Long lastSeenTs;
        private boolean online;
        private final Deque<RuntimeStatus> recentStatuses = new ArrayDeque<>();
    }
}
