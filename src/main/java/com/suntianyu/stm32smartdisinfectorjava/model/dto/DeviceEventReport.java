package com.suntianyu.stm32smartdisinfectorjava.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class DeviceEventReport {
    @JsonAlias("t")
    private String type; // "event_report"

    @JsonAlias("id")
    private String deviceId;

    private Long seq;
    private Long ts;

    @JsonAlias("e")
    private String eventCode;

    private String eventSource;
    private String message;

    @JsonAlias("s")
    private String systemStatus;

    @JsonAlias("m")
    private String mode;

    @JsonAlias("sg")
    private String stage;

    @JsonAlias("tp")
    private Double temperature;

    @JsonAlias("hm")
    private Double humidity;

    @JsonAlias("d")
    private Boolean doorOpen;

    @JsonAlias("r")
    private Boolean machineRunning;

    @JsonAlias("p")
    private Boolean paused;

    @JsonAlias("h")
    private Boolean heaterOn;

    @JsonAlias("u")
    private Boolean disinfectionOn;

    @JsonAlias("f")
    private Boolean fanOn;

    @JsonAlias("du")
    private Integer duration;

    @JsonAlias("rs")
    private Integer remainingSeconds;

    @JsonAlias("tl")
    private Double tempLow;

    @JsonAlias("th")
    private Double tempHigh;

    @JsonAlias("hl")
    private Double humidityLow;

    @JsonAlias("hh")
    private Double humidityHigh;
}
