package com.suntianyu.stm32smartdisinfectorjava.model.dto;

import com.suntianyu.stm32smartdisinfectorjava.model.enums.DisinfectorMode;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RuntimeStatus {
    private String deviceId;
    private boolean deviceOnline;
    private Long lastSeenTs;
    private boolean machineRunning;
    private boolean paused;
    private DisinfectorMode selectedMode;
    private Integer duration;
    private Integer remainingSeconds;
    private Double temperature;
    private Double humidity;
    private boolean doorOpen;
    private boolean heaterOn;
    private boolean disinfectionOn;
    private boolean fanOn;
    private Double tempLow;
    private Double tempHigh;
    private Double humidityLow;
    private Double humidityHigh;
    private Integer faultCode;
    private String systemStatus;
    private String fwVersion;
    private String lastEventCode;
    private String lastEventMessage;
    private String lastEventSource;
    private Long lastEventTs;
    private LocalDateTime updatedAt;
}
