package com.suntianyu.stm32smartdisinfectorjava.model.dto;

import com.suntianyu.stm32smartdisinfectorjava.model.enums.DisinfectorMode;
import lombok.Data;

@Data
public class StartTaskRequest {
    private DisinfectorMode mode;
    private Integer duration;
    private Thresholds thresholds;
}

