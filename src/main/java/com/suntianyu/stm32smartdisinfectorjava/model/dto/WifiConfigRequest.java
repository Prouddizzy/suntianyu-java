package com.suntianyu.stm32smartdisinfectorjava.model.dto;

import lombok.Data;

@Data
public class WifiConfigRequest {
    private String ssid;
    private String password;
}
