package com.suntianyu.stm32smartdisinfectorjava.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DisinfectorMode {
    HEATING("\u52a0\u70ed\u6a21\u5f0f", "heat", "heating"),
    DISINFECTION("\u6d88\u6bd2\u6a21\u5f0f", "uv", "disinfection", "disinfect"),
    FAN("\u98ce\u6247\u6a21\u5f0f", "fan"),
    SMART("\u667a\u80fd\u6a21\u5f0f", "smart");

    private final String value;
    private final String[] deviceAliases;

    DisinfectorMode(String value, String... deviceAliases) {
        this.value = value;
        this.deviceAliases = deviceAliases;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public String toDeviceKey() {
        return deviceAliases[0];
    }

    @JsonCreator
    public static DisinfectorMode fromValue(String value) {
        return fromDeviceValue(value);
    }

    public static DisinfectorMode fromDeviceValue(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        for (DisinfectorMode mode : values()) {
            if (mode.value.equals(trimmed)) {
                return mode;
            }
            for (String alias : mode.deviceAliases) {
                if (alias.equalsIgnoreCase(trimmed)) {
                    return mode;
                }
            }
        }
        return null;
    }
}
