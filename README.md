# SIP Bot (Spring Boot + JAIN-SIP + Netty)

Sample Spring Boot 2.6.13 application that registers to a FreeSWITCH server, accepts inbound calls, and plays audio (WAV or synthesized tone-based TTS) over RTP using Netty.

## Features
- SIP registration with digest authentication against FreeSWITCH.
- INVITE handling with automatic 180 Ringing and 200 OK with PCMU/8000 SDP.
- RTP playback of a configured WAV file, or a synthesized tone sequence generated from text when no file is available.
- Optional automatic BYE after playback completes.

## Requirements
- JDK 11+
- Maven 3.6+
- Network reachability between the application host and FreeSWITCH for SIP and RTP.

## Configuration
Edit `src/main/resources/application.yml` or provide environment variables to adjust SIP details:

```yaml
sip:
  local-address: 192.168.56.1        # IP visible to FreeSWITCH for SIP/RTP
  port: 5060                        # SIP listening port
  transport: udp                    # udp or tcp
  rtp-port: 4000                    # RTP port advertised in SDP and used for media
  domain: 192.168.56.10             # FreeSWITCH domain/registrar
  username: 1000                    # Extension/user to register
  password: yourFreeSwitchPassword
  register-ttl-seconds: 3600        # Registration refresh interval
  audio-file: audio/demo.wav        # WAV file to stream; if missing, synthesized tones are used
  tts-text: "Welcome to the Java SIP bot"
  hangup-after-playback: true       # Send BYE when playback is finished
```

Place your WAV file on disk (mono, 8 kHz is preferred). The application converts it to µ-law (PCMU) on the fly.

## Running locally
```bash
mvn clean package
java -jar target/sipbot-0.0.1-SNAPSHOT.jar
```

The application immediately starts the SIP stack, registers to FreeSWITCH, and waits for incoming calls. Once an INVITE is ACKed, it streams the configured audio to the RTP endpoint advertised in the caller's SDP.

## Notes
- The sample uses payload type 0 (PCMU/8000) with 20 ms packets. Ensure the peer advertises/accepts PCMU.
- If the WAV file cannot be loaded, a simple tone-based TTS fallback renders the configured text into audio.
- `netty-all` is used for lightweight RTP packet delivery over UDP.
