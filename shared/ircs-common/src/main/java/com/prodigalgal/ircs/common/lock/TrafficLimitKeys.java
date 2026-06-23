package com.prodigalgal.ircs.common.lock;

public final class TrafficLimitKeys {

    public static final String PREFIX = "traffic:limit:";
    public static final String ACTIVE_INDEX_KEY = "traffic:limit:index";

    private TrafficLimitKeys() {
    }

    public static boolean isTrafficKey(String key) {
        return key != null && key.startsWith(PREFIX);
    }
}
