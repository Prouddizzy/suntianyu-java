package com.suntianyu.stm32smartdisinfectorjava.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WifiConfigResponse {
    private String deviceId;
    private String ssid;
    private boolean appliedAfterRestart;
}
