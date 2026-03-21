package com.suntianyu.stm32smartdisinfectorjava.model.dto;

import lombok.Data;

@Data
public class DeviceCommandAck {
    private String type; // "ack"
    private String cmdId;
    private String deviceId;
    private boolean ok;
    private int code;
    private String message;
    private Long ts;
}

