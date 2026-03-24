package com.suntianyu.stm32smartdisinfectorjava.service;

import com.suntianyu.stm32smartdisinfectorjava.model.dto.RuntimeStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class DeviceStatusStreamService {

    private static final long SSE_TIMEOUT_MS = 30L * 60L * 1000L;
    private static final String DEFAULT_DEVICE_ID = "STM-001";

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String deviceId) {
        String resolvedDeviceId = hasText(deviceId) ? deviceId : DEFAULT_DEVICE_ID;
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitters.computeIfAbsent(resolvedDeviceId, key -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(resolvedDeviceId, emitter));
        emitter.onTimeout(() -> {
            removeEmitter(resolvedDeviceId, emitter);
            emitter.complete();
        });
        emitter.onError(ex -> {
            removeEmitter(resolvedDeviceId, emitter);
            emitter.completeWithError(ex);
        });

        return emitter;
    }

    public void sendSnapshot(SseEmitter emitter, RuntimeStatus status) {
        if (emitter == null || status == null) {
            return;
        }

        try {
            emitter.send(SseEmitter.event().name("status").data(status));
        } catch (IOException e) {
            log.warn("Failed to send initial SSE snapshot", e);
            emitter.completeWithError(e);
        }
    }

    public void publish(RuntimeStatus status) {
        if (status == null || !hasText(status.getDeviceId())) {
            return;
        }

        CopyOnWriteArrayList<SseEmitter> subscribers = emitters.get(status.getDeviceId());
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event().name("status").data(status));
            } catch (IOException e) {
                removeEmitter(status.getDeviceId(), emitter);
                emitter.completeWithError(e);
            }
        }
    }

    @Scheduled(fixedDelay = 10000L)
    public void heartbeat() {
        emitters.forEach((deviceId, subscribers) -> {
            for (SseEmitter emitter : subscribers) {
                try {
                    emitter.send(SseEmitter.event().name("ping").data("ok"));
                } catch (IOException e) {
                    removeEmitter(deviceId, emitter);
                    emitter.completeWithError(e);
                }
            }
        });
    }

    private void removeEmitter(String deviceId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> subscribers = emitters.get(deviceId);
        if (subscribers == null) {
            return;
        }

        subscribers.remove(emitter);
        if (subscribers.isEmpty()) {
            emitters.remove(deviceId);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
