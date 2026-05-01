package com.tianji.aigc.service.impl;

import com.tianji.aigc.service.AudioService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@Primary
@Profile("dev-demo")
public class DevDemoAudioService implements AudioService {

    @Override
    public ResponseBodyEmitter ttsStream(String text) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter();
        try {
            String payload = "dev-demo tts preview: " + text;
            emitter.send(payload.getBytes(StandardCharsets.UTF_8));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    @Override
    public String stt(MultipartFile audioFile) {
        String fileName = audioFile == null ? "unknown-audio" : audioFile.getOriginalFilename();
        log.info("dev-demo stt request, fileName={}", fileName);
        return "这是 dev-demo 模式下的语音转写结果，文件名：" + fileName;
    }
}
