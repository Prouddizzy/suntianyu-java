package com.suntianyu.stm32smartdisinfectorjava.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class DeviceCommandAck {
    @JsonAlias("t")
    private String type; // "ack"

    @JsonAlias("c")
    private String cmdId;

    @JsonAlias("id")
    private String deviceId;

    private boolean ok;

    @JsonAlias("ec")
    private int code;

    private String message;
    private Long ts;
}

