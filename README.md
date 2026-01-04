# SIP 机器人（Spring Boot + JAIN-SIP + Netty）

Spring Boot 2.6.13 示例程序，向 FreeSWITCH 注册并接受来电，接听后通过 Netty 的 RTP 推送 WAV 或简易 TTS（音调合成）音频。

## 功能
- 使用 JAIN-SIP 完成注册与摘要认证。
- 处理 INVITE，自动返回 180 Ringing 和包含 PCMU/8000 SDP 的 200 OK。
- 通过 Netty 以 RTP 发送配置的 WAV，若文件缺失则用文本生成的音调序列播放。
- 播放结束后可选自动发送 BYE。

## 前置条件
- JDK 11+
- Maven 3.6+
- 应用主机与 FreeSWITCH 之间 SIP/RTP 可互通。

## 配置
在 `src/main/resources/application.yml` 中修改，或通过环境变量覆盖：

```yaml
sip:
  local-address: 192.168.56.1        # FreeSWITCH 可见的本机 IP（SIP/RTP）
  port: 5060                         # SIP 监听端口
  transport: udp                     # udp 或 tcp
  rtp-port: 4000                     # SDP 中声明且本地发送 RTP 使用的端口
  domain: 192.168.56.10              # FreeSWITCH 域名/注册服务器
  username: 1000                     # 注册的分机/用户
  password: yourFreeSwitchPassword
  register-ttl-seconds: 3600         # 注册刷新周期
  audio-file: audio/demo.wav         # 要播放的 WAV；缺失时会使用文本合成音调
  tts-text: "Welcome to the Java SIP bot"
  hangup-after-playback: true        # 播放结束后是否自动发送 BYE
```

请将 WAV 文件放到磁盘上（建议单声道 8 kHz）。应用会即时转换为 µ-law（PCMU）。

## 本地运行
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

应用启动后立刻启用 SIP 栈，向 FreeSWITCH 注册并等待来电。收到 INVITE 并完成 ACK 后，会把音频推送到主叫 SDP 中的 RTP 地址。

## 说明
- 示例使用载荷类型 0（PCMU/8000），20 ms 一帧，请确认对端支持 PCMU。
- 若 WAV 加载失败，会用简单的音调合成把配置文本转换为音频。
- 使用 `netty-all` 通过 UDP 发送 RTP，代码轻量便于嵌入。
The application immediately starts the SIP stack, registers to FreeSWITCH, and waits for incoming calls. Once an INVITE is ACKed, it streams the configured audio to the RTP endpoint advertised in the caller's SDP.

## Notes
- The sample uses payload type 0 (PCMU/8000) with 20 ms packets. Ensure the peer advertises/accepts PCMU.
- If the WAV file cannot be loaded, a simple tone-based TTS fallback renders the configured text into audio.
- `netty-all` is used for lightweight RTP packet delivery over UDP.
