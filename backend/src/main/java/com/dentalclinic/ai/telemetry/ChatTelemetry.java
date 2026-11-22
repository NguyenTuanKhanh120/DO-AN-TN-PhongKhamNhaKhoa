package com.dentalclinic.ai.telemetry;

import org.slf4j.MDC;

import java.util.UUID;

public final class ChatTelemetry {

    private static final String REQUEST_ID_KEY = "chatRequestId";
    private static final String UNSCOPED_REQUEST_ID = "unscoped";

    private ChatTelemetry() {
    }

    public static String newRequestId() {
        return UUID.randomUUID().toString();
    }

    public static MDC.MDCCloseable open(String requestId) {
        return MDC.putCloseable(REQUEST_ID_KEY, requestId);
    }

    public static String currentRequestId() {
        String requestId = MDC.get(REQUEST_ID_KEY);
        return requestId == null || requestId.isBlank()
                ? UNSCOPED_REQUEST_ID
                : requestId;
    }

    public static long elapsedMillis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }
}
