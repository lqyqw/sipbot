package com.example.sipbot.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

@Component
public class AudioFileLoader {

    private static final Logger log = LoggerFactory.getLogger(AudioFileLoader.class);

    public byte[] loadMuLawSamples(Path path) {
        File file = path.toFile();
        if (!file.exists()) {
            log.warn("Audio file {} not found; falling back to synthesized tones", path);
            return new byte[0];
        }

        AudioFormat targetFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                8000f, 16, 1, 2, 8000f, false);

        try (AudioInputStream inputStream = AudioSystem.getAudioInputStream(file);
             AudioInputStream pcmStream = AudioSystem.getAudioInputStream(targetFormat, inputStream)) {

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[4096];
            int bytesRead;
            while ((bytesRead = pcmStream.read(data)) != -1) {
                buffer.write(data, 0, bytesRead);
            }
            byte[] pcm = buffer.toByteArray();
            return MuLawCodec.encodePcm16LeToMuLaw(pcm);
        } catch (UnsupportedAudioFileException | IOException e) {
            log.error("Unable to load audio from {}", path, e);
            return new byte[0];
        }
    }
}
