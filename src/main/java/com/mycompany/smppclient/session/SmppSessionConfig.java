package com.mycompany.smppclient.session;

public class SmppSessionConfig {
    private final int responseTimeoutMs ;
    private final int enquireLinkIntervalMs;

    public SmppSessionConfig(int responseTimeoutMs, int enquireLinkIntervalMs){
        this.responseTimeoutMs = responseTimeoutMs;
        this.enquireLinkIntervalMs = enquireLinkIntervalMs;

    }

    public int getResponseTimeoutMs() {
        return responseTimeoutMs;
    }

    public int getEnquireLinkIntervalMs() {
        return enquireLinkIntervalMs;
    }
}
