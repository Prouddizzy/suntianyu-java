package com.suntianyu.stm32smartdisinfectorjava.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceCommand {
    @JsonProperty("t")
    private String type;

    @JsonProperty("c")
    private String cmdId;

    @JsonProperty("id")
    private String deviceId;

    @JsonProperty("a")
    private String action;

    @JsonProperty("m")
    private String mode;

    @JsonProperty("du")
    private Integer duration;

    @JsonProperty("tl")
    private Double tempLow;

    @JsonProperty("th")
    private Double tempHigh;

    @JsonProperty("hl")
    private Double humidityLow;

    @JsonProperty("hh")
    private Double humidityHigh;

    @JsonProperty("ws")
    private String wifiSsid;

    @JsonProperty("wp")
    private String wifiPassword;
}
