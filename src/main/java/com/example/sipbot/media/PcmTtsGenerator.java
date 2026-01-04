package com.example.sipbot.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;

@Component
public class PcmTtsGenerator {

    private static final Logger log = LoggerFactory.getLogger(PcmTtsGenerator.class);
    private static final float SAMPLE_RATE = 8000f;

    public byte[] synthesizeMuLaw(String text) {
        if (text == null || text.isEmpty()) {
            return new byte[0];
        }
        log.info("Synthesizing tones for text: {}", text);
        byte[] pcm = synthesizePcm(text);
        return MuLawCodec.encodePcm16LeToMuLaw(pcm);
    }

    private byte[] synthesizePcm(String text) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        double twoPi = 2 * Math.PI;
        int samplesPerTone = (int) (SAMPLE_RATE * 0.25); // 250ms per character
        int gapSamples = (int) (SAMPLE_RATE * 0.05); // small pause between characters

        for (char c : text.toCharArray()) {
            double frequency = 400 + (c % 32) * 20;
            for (int i = 0; i < samplesPerTone; i++) {
                double angle = twoPi * frequency * i / SAMPLE_RATE;
                short sample = (short) (Math.sin(angle) * Short.MAX_VALUE * 0.2);
                output.write(sample & 0xFF);
                output.write((sample >> 8) & 0xFF);
            }
            for (int i = 0; i < gapSamples; i++) {
                output.write(0);
                output.write(0);
            }
        }
        return output.toByteArray();
    }
}
