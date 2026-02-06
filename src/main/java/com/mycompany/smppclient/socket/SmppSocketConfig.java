package com.mycompany.smppclient.socket;

public class SmppSocketConfig {
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int maxReconnectAttempts;
    private final int reconnectBackoffMs;

    public SmppSocketConfig(int connectTimeoutMs, int readTimeoutMs, int maxReconnectAttempts, int reconnectBackoffMs) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.maxReconnectAttempts = maxReconnectAttempts;
        this.reconnectBackoffMs = reconnectBackoffMs;
    }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public int getMaxReconnectAttempts() { return maxReconnectAttempts; }
    public int getReconnectBackoffMs() { return reconnectBackoffMs; }

    public static SmppSocketConfig defaults() {
        return new SmppSocketConfig(
                5000, // connectTimeoutMs
                5000, // readTimeoutMs
                3,    // maxReconnectAttempts
                1000  // reconnectBackoffMs
        );
    }
}
