package com.example.sipbot.sip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Optional;

public final class SdpParser {

    private static final Logger log = LoggerFactory.getLogger(SdpParser.class);

    private SdpParser() {
    }

    public static Optional<SdpDetails> parse(String sdp) {
        if (sdp == null || sdp.isEmpty()) {
            return Optional.empty();
        }
        String connection = null;
        Integer port = null;
        for (String line : sdp.split("\r?\n")) {
            if (line.startsWith("c=")) {
                String[] parts = line.split(" ");
                if (parts.length >= 3) {
                    connection = parts[2];
                }
            }
            if (line.startsWith("m=audio")) {
                String[] parts = line.split(" ");
                if (parts.length >= 2) {
                    port = Integer.parseInt(parts[1]);
                }
            }
        }
        if (connection == null || port == null) {
            log.warn("SDP missing connection or audio port: {}", sdp);
            return Optional.empty();
        }
        try {
            String host = InetAddress.getByName(connection).getHostAddress();
            return Optional.of(new SdpDetails(host, port));
        } catch (UnknownHostException e) {
            log.warn("Unable to resolve SDP host {}", connection, e);
            return Optional.empty();
        }
    }

    public static String buildAnswer(String localAddress, int rtpPort) {
        String[] lines = new String[]{
                "v=0",
                "o=sipbot 0 0 IN IP4 " + localAddress,
                "s=sipbot",
                "c=IN IP4 " + localAddress,
                "t=0 0",
                "m=audio " + rtpPort + " RTP/AVP 0",
                "a=rtpmap:0 PCMU/8000",
                "a=ptime:20"
        };
        return String.join("\r\n", Arrays.asList(lines));
    }
}
