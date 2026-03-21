package com.suntianyu.stm32smartdisinfectorjava.model.dto;

import com.suntianyu.stm32smartdisinfectorjava.model.enums.DisinfectorMode;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RuntimeStatus {
    private boolean machineRunning;
    private boolean paused;
    private DisinfectorMode selectedMode;
    // in minutes
    private Integer duration;
    // in seconds
    private Integer remainingSeconds;

    private Double temperature;
    private Double humidity;

    private boolean doorOpen;

    private boolean heaterOn;
    private boolean disinfectionOn;
    private boolean fanOn;

    // Configs reflected in status
    private Double tempLow;
    private Double tempHigh;
    private Double humidityLow;
    private Double humidityHigh;

    private Integer faultCode;

    private LocalDateTime updatedAt;
}
