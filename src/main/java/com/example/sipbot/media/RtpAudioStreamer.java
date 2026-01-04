package com.example.sipbot.media;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class RtpAudioStreamer {

    private static final Logger log = LoggerFactory.getLogger(RtpAudioStreamer.class);

    public Closeable stream(String remoteHost, int remotePort, int localPort, byte[] muLaw, Runnable onFinished) {
        if (muLaw.length == 0) {
            log.warn("No audio payload available; skipping RTP stream");
            return () -> { };
        }

        EventLoopGroup group = new NioEventLoopGroup(1);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .handler(new io.netty.channel.SimpleChannelInboundHandler<DatagramPacket>() {
                        @Override
                        protected void channelRead0(io.netty.channel.ChannelHandlerContext ctx, DatagramPacket msg) {
                            // 播放流程无需处理入站数据。
                            // No inbound handling required for playback.
                        }
                    });

            Channel channel = bootstrap.bind(localPort).sync().channel();
            log.info("Streaming {} bytes of mu-law audio to {}:{} from local UDP {}", muLaw.length, remoteHost, remotePort, localPort);

            AtomicBoolean closed = new AtomicBoolean(false);
            Runnable shutdown = () -> {
                if (closed.compareAndSet(false, true)) {
                    scheduler.shutdownNow();
                    channel.close();
                    group.shutdownGracefully();
                    if (onFinished != null) {
                        onFinished.run();
                    }
                }
            };

            scheduler.scheduleAtFixedRate(new FrameSender(muLaw, channel, remoteHost, remotePort, shutdown), 0, 20, TimeUnit.MILLISECONDS);
            return shutdown::run;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            group.shutdownGracefully();
            scheduler.shutdownNow();
            throw new IllegalStateException("Failed to start RTP streaming", e);
        }
    }

    private static class FrameSender implements Runnable {
        private final byte[] audio;
        private final Channel channel;
        private final InetSocketAddress remote;
        private final Runnable shutdown;
        private int cursor = 0;
        private int sequence = 0;
        private long timestamp = 0;
        private final int ssrc = (int) (System.nanoTime() & 0x7FFFFFFF);

        FrameSender(byte[] audio, Channel channel, String remoteHost, int remotePort, Runnable shutdown) {
            this.audio = audio;
            this.channel = channel;
            this.remote = new InetSocketAddress(remoteHost, remotePort);
            this.shutdown = shutdown;
        }

        @Override
        public void run() {
            try {
                if (cursor >= audio.length) {
                    shutdown.run();
                    return;
                }
                int payloadSize = Math.min(160, audio.length - cursor);
                byte[] packet = new byte[12 + payloadSize];
                packet[0] = (byte) 0x80; // RTP 版本 2
                packet[1] = 0; // 负载类型 0（PCMU）
                packet[0] = (byte) 0x80; // V2
                packet[1] = 0; // PT=0 (PCMU)
                packet[2] = (byte) ((sequence >> 8) & 0xFF);
                packet[3] = (byte) (sequence & 0xFF);
                packet[4] = (byte) ((timestamp >> 24) & 0xFF);
                packet[5] = (byte) ((timestamp >> 16) & 0xFF);
                packet[6] = (byte) ((timestamp >> 8) & 0xFF);
                packet[7] = (byte) (timestamp & 0xFF);
                packet[8] = (byte) ((ssrc >> 24) & 0xFF);
                packet[9] = (byte) ((ssrc >> 16) & 0xFF);
                packet[10] = (byte) ((ssrc >> 8) & 0xFF);
                packet[11] = (byte) (ssrc & 0xFF);
                System.arraycopy(audio, cursor, packet, 12, payloadSize);

                channel.writeAndFlush(new DatagramPacket(Unpooled.wrappedBuffer(packet), remote));
                cursor += payloadSize;
                sequence++;
                timestamp += payloadSize;
            } catch (Exception e) {
                shutdown.run();
            }
        }
    }
}
