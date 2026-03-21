package com.suntianyu.stm32smartdisinfectorjava.model.dto;

import lombok.Data;

@Data
public class Thresholds {
    private Double tempLow;
    private Double tempHigh;
    private Double humidityLow;
    private Double humidityHigh;
}

