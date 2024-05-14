package com.saayaam.infrastructure.metadata;

import java.util.Arrays;

public enum AZ {
    US_EAST_1A("us-east-1a"),
    US_EAST_1B("us-east-1b");

    private final String value;

    AZ(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static AZ fromAZString(String azString) {
        return Arrays.stream(values())
                .filter(az -> az.value.equals(azString))
                .findFirst()
                .orElseThrow();
    }
}
