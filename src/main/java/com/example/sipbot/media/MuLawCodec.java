package com.example.sipbot.media;

public final class MuLawCodec {
    private static final int BIAS = 0x84;
    private static final int MAX = 32635;

    private MuLawCodec() {
    }

    public static byte[] encodePcm16LeToMuLaw(byte[] pcm) {
        byte[] encoded = new byte[pcm.length / 2];
        for (int i = 0, j = 0; i < pcm.length; i += 2, j++) {
            int low = pcm[i] & 0xFF;
            int high = pcm[i + 1];
            short sample = (short) ((high << 8) | low);
            encoded[j] = linearToMuLaw(sample);
        }
        return encoded;
    }

    private static byte linearToMuLaw(short sample) {
        int sign = (sample >> 8) & 0x80;
        if (sign != 0) {
            sample = (short) -sample;
        }
        if (sample > MAX) {
            sample = MAX;
        }
        sample = (short) (sample + BIAS);
        int exponent = 7;
        for (int expMask = 0x4000; (sample & expMask) == 0 && exponent > 0; exponent--, expMask >>= 1) {
            // 通过右移寻找指数位。
        }
        int mantissa = (sample >> ((exponent == 0) ? 4 : (exponent + 3))) & 0x0F;
        byte muLaw = (byte) (~(sign | (exponent << 4) | mantissa));
        return muLaw;
    }
}
