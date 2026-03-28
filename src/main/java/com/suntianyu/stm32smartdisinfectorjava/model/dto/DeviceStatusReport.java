package com.suntianyu.stm32smartdisinfectorjava.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class DeviceStatusReport {
    @JsonAlias("t")
    private String type; // "status_report"

    @JsonAlias("id")
    private String deviceId;

    private Long seq;
    private Long ts;

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

    @JsonAlias("m")
    private String mode;

    @JsonAlias("s")
    private String systemStatus;

    @JsonAlias("sg")
    private String stage;

    @JsonAlias("h")
    private Boolean heaterOn;

    @JsonAlias("u")
    private Boolean disinfectionOn;

    @JsonAlias("f")
    private Boolean fanOn;

    @JsonAlias("rs")
    private Integer remainingSeconds;

    @JsonAlias("du")
    private Integer duration;

    @JsonAlias("fc")
    private Integer faultCode;

    @JsonAlias("tl")
    private Double tempLow;

    @JsonAlias("th")
    private Double tempHigh;

    @JsonAlias("hl")
    private Double humidityLow;

    @JsonAlias("hh")
    private Double humidityHigh;

    @JsonAlias("fw")
    private String fwVersion;
}
