package com.suntianyu.stm32smartdisinfectorjava.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.RuntimeStatus;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.Thresholds;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisStateRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String KEY_STATE_PREFIX = "disinfector:state:";
    private static final String KEY_CONFIG_PREFIX = "disinfector:config:";
    private static final String KEY_CMD_PREFIX = "disinfector:cmd:";
    private static final String KEY_LAST_SEEN_PREFIX = "disinfector:last_seen:";

    public void saveStatus(String deviceId, RuntimeStatus status) {
        String key = KEY_STATE_PREFIX + deviceId;
        // Convert to map to store as Hash
        Map<String, Object> map = objectMapper.convertValue(status, new TypeReference<Map<String, Object>>() {});
        redisTemplate.opsForHash().putAll(key, map);
        redisTemplate.expire(key, 1, TimeUnit.HOURS); // Auto expire if no update
    }

    public RuntimeStatus getStatus(String deviceId) {
        String key = KEY_STATE_PREFIX + deviceId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return null;
        }
        return objectMapper.convertValue(entries, RuntimeStatus.class);
    }

    public void saveConfig(String deviceId, Thresholds thresholds) {
        String key = KEY_CONFIG_PREFIX + deviceId;
        Map<String, Object> map = objectMapper.convertValue(thresholds, new TypeReference<Map<String, Object>>() {});
        redisTemplate.opsForHash().putAll(key, map);
    }

    public Thresholds getConfig(String deviceId) {
        String key = KEY_CONFIG_PREFIX + deviceId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return null;
        }
        return objectMapper.convertValue(entries, Thresholds.class);
    }

    public void updateLastSeen(String deviceId) {
        String key = KEY_LAST_SEEN_PREFIX + deviceId;
        redisTemplate.opsForValue().set(key, String.valueOf(System.currentTimeMillis()));
    }

    public boolean isOnline(String deviceId) {
         String key = KEY_LAST_SEEN_PREFIX + deviceId;
         String val = (String) redisTemplate.opsForValue().get(key);
         if (val == null) return false;
         long lastSeen = Long.parseLong(val);
         // 10 seconds timeout as per guide
         return System.currentTimeMillis() - lastSeen < 10000;
    }
}
