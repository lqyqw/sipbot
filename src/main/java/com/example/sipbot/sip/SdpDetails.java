package com.example.sipbot.sip;

public class SdpDetails {
    private final String remoteHost;
    private final int remotePort;

    public SdpDetails(String remoteHost, int remotePort) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public int getRemotePort() {
        return remotePort;
    }
}
