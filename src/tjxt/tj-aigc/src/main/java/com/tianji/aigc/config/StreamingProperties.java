package com.tianji.aigc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Streaming safety valve. The SSE event buffer is bounded so a slow consumer cannot
 * grow memory without limit; overflow drops intermediate token chunks (the full
 * assistant text is still assembled in the terminal payload). Terminal STOP/TRACE/PARAM
 * events are emitted on the terminal path regardless of the data buffer.
 */
@Configuration
@ConfigurationProperties(prefix = "tj.ai.streaming")
public class StreamingProperties {

    /** Bounded sink queue size for SSE data events. */
    private int bufferSize = 256;

    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        if (bufferSize < 1) {
            throw new IllegalArgumentException("tj.ai.streaming.buffer-size must be >= 1");
        }
        this.bufferSize = bufferSize;
    }
}
