package com.suntianyu.stm32smartdisinfectorjava.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DisinfectorMode {
    HEATING("加热模式"),
    DISINFECTION("消毒模式"),
    SMART("智能模式");

    private final String value;

    DisinfectorMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static DisinfectorMode fromValue(String value) {
        for (DisinfectorMode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        return null;
    }
}
