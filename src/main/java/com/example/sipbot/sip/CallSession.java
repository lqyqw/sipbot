package com.example.sipbot.sip;

import javax.sip.Dialog;
import javax.sip.ServerTransaction;

public class CallSession {
    private final String callId;
    private final Dialog dialog;
    private final String remoteHost;
    private final int remoteRtpPort;
    private final ServerTransaction serverTransaction;

    public CallSession(String callId, Dialog dialog, String remoteHost, int remoteRtpPort, ServerTransaction serverTransaction) {
        this.callId = callId;
        this.dialog = dialog;
        this.remoteHost = remoteHost;
        this.remoteRtpPort = remoteRtpPort;
        this.serverTransaction = serverTransaction;
    }

    public String getCallId() {
        return callId;
    }

    public Dialog getDialog() {
        return dialog;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public int getRemoteRtpPort() {
        return remoteRtpPort;
    }

    public ServerTransaction getServerTransaction() {
        return serverTransaction;
    }
}
