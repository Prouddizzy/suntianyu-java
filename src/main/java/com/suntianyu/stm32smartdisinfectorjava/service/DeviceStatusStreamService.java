package com.suntianyu.stm32smartdisinfectorjava.service;

import com.suntianyu.stm32smartdisinfectorjava.model.dto.RuntimeStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Locale;
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
            log.debug("SSE stream timed out, deviceId={}", resolvedDeviceId);
            completeEmitter(resolvedDeviceId, emitter);
        });
        emitter.onError(ex -> {
            logEmitterIssue(resolvedDeviceId, ex, "stream callback");
            completeEmitter(resolvedDeviceId, emitter);
        });

        return emitter;
    }

    public void sendSnapshot(SseEmitter emitter, RuntimeStatus status) {
        if (emitter == null || status == null) {
            return;
        }

        String deviceId = hasText(status.getDeviceId()) ? status.getDeviceId() : DEFAULT_DEVICE_ID;
        try {
            emitter.send(SseEmitter.event().data(status));
        } catch (IOException | IllegalStateException e) {
            handleEmitterFailure(deviceId, emitter, e, "initial snapshot");
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
                emitter.send(SseEmitter.event().data(status));
            } catch (IOException | IllegalStateException e) {
                handleEmitterFailure(status.getDeviceId(), emitter, e, "status publish");
            }
        }
    }

    @Scheduled(fixedDelay = 10000L)
    public void heartbeat() {
        emitters.forEach((deviceId, subscribers) -> {
            for (SseEmitter emitter : subscribers) {
                try {
                    emitter.send(SseEmitter.event().name("ping").data("ok"));
                } catch (IOException | IllegalStateException e) {
                    handleEmitterFailure(deviceId, emitter, e, "heartbeat");
                }
            }
        });
    }

    private void handleEmitterFailure(String deviceId, SseEmitter emitter, Exception exception, String action) {
        logEmitterIssue(deviceId, exception, action);
        completeEmitter(deviceId, emitter);
    }

    private void completeEmitter(String deviceId, SseEmitter emitter) {
        removeEmitter(deviceId, emitter);
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // The emitter may already be completed by the container thread.
        }
    }

    private void logEmitterIssue(String deviceId, Throwable throwable, String action) {
        if (isClientDisconnect(throwable)) {
            log.debug("SSE client disconnected during {}, deviceId={}", action, deviceId);
            return;
        }
        log.warn("Failed during SSE {}, deviceId={}", action, deviceId, throwable);
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

    private boolean isClientDisconnect(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String className = current.getClass().getName();
            if (className.endsWith("AsyncRequestNotUsableException")
                    || className.contains("ClientAbortException")) {
                return true;
            }

            if (hasDisconnectMessage(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean hasDisconnectMessage(String message) {
        if (!hasText(message)) {
            return false;
        }

        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("broken pipe")
                || normalized.contains("connection reset by peer")
                || normalized.contains("forcibly closed by the remote host")
                || normalized.contains("established connection was aborted");
    }
}
