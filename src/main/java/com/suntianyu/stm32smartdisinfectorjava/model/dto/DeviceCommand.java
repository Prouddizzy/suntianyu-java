package com.suntianyu.stm32smartdisinfectorjava.model.dto;

import com.suntianyu.stm32smartdisinfectorjava.model.enums.DisinfectorMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCommand {
    private String type; // "control_cmd"
    private String cmdId;
    private String deviceId;
    private String action; // start, pause, stop

    // For start command
    private DisinfectorMode mode;
    private Integer duration;
    private Thresholds thresholds;
}

