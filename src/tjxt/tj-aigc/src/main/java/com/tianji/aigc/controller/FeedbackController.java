package com.tianji.aigc.controller;

import com.tianji.aigc.feedback.FeedbackRecord;
import com.tianji.aigc.feedback.FeedbackService;
import com.tianji.common.annotations.NoWrapper;
import com.tianji.common.exceptions.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * User feedback on assistant messages. Validated server-side: run must exist, rating must
 * be a known code, comment is length-capped and redacted.
 */
@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    @NoWrapper
    public FeedbackRecord submit(@RequestBody Map<String, Object> body) {
        try {
            return feedbackService.submit(
                    String.valueOf(body.getOrDefault("runId", "")),
                    String.valueOf(body.getOrDefault("messageId", "")),
                    String.valueOf(body.getOrDefault("rating", "")),
                    body.get("reasonCode") == null ? null : String.valueOf(body.get("reasonCode")),
                    body.get("comment") == null ? null : String.valueOf(body.get("comment")));
        }
        catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }
}
