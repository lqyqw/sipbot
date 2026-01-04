package com.example.sipbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sip")
public class SipProperties {

    /**
     * FreeSWITCH 可访问的本机 IP（SIP 与 RTP）。
     * IP address reachable by FreeSWITCH for SIP and RTP.
     */
    private String localAddress = "127.0.0.1";

    /**
     * SIP 监听端口。
     * SIP listening port.
     */
    private int port = 5060;

    /**
     * 传输协议（udp 或 tcp）。
     * UDP or TCP.
     */
    private String transport = "udp";

    /**
     * SDP 中声明的 RTP 端口，同时作为 Netty 绑定端口。
     * RTP port used in the SDP answer and as the local bind for Netty.
     */
    private int rtpPort = 4000;

    /**
     * 注册服务器或 FreeSWITCH 域。
     * Registrar or FreeSWITCH domain.
     */
    private String domain = "127.0.0.1";

    private String username = "1000";
    private String password = "super-secret";

    /**
     * 注册有效期（秒）。
     * Seconds before refresh.
     */
    private int registerTtlSeconds = 3600;

    /**
     * 接听后播放的 WAV 文件路径。
     * File system path to the WAV file played after answering.
     */
    private String audioFile = "audio/hello.wav";

    /**
     * 未配置 WAV 时，用于生成简易音调 TTS 的文本。
     * Text rendered as a simple synthesized tone sequence when no WAV is configured.
     */
    private String ttsText = "Welcome to the Java SIP bot";

    private boolean hangupAfterPlayback = true;

    public String getLocalAddress() {
        return localAddress;
    }

    public void setLocalAddress(String localAddress) {
        this.localAddress = localAddress;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    public int getRtpPort() {
        return rtpPort;
    }

    public void setRtpPort(int rtpPort) {
        this.rtpPort = rtpPort;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getRegisterTtlSeconds() {
        return registerTtlSeconds;
    }

    public void setRegisterTtlSeconds(int registerTtlSeconds) {
        this.registerTtlSeconds = registerTtlSeconds;
    }

    public String getAudioFile() {
        return audioFile;
    }

    public void setAudioFile(String audioFile) {
        this.audioFile = audioFile;
    }

    public String getTtsText() {
        return ttsText;
    }

    public void setTtsText(String ttsText) {
        this.ttsText = ttsText;
    }

    public boolean isHangupAfterPlayback() {
        return hangupAfterPlayback;
    }

    public void setHangupAfterPlayback(boolean hangupAfterPlayback) {
        this.hangupAfterPlayback = hangupAfterPlayback;
    }
}
