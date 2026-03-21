package com.suntianyu.stm32smartdisinfectorjava.model.dto;

import lombok.Data;

@Data
public class DeviceStatusReport {
    private String type; // "status_report"
    private String deviceId;
    private Long seq;
    private Long ts;

    private Double temperature;
    private Double humidity;
    private boolean doorOpen;
    private boolean machineRunning;
    private boolean paused;
    private String mode; // String to match JSON, map to enum later

    private boolean heaterOn;
    private boolean disinfectionOn;
    private boolean fanOn;

    private Integer remainingSeconds;
    private Integer duration; // Optional but good to have
    private Integer faultCode;

    // Thresholds might be reported back
    private Double tempLow;
    private Double tempHigh;
    private Double humidityLow;
    private Double humidityHigh;
}

