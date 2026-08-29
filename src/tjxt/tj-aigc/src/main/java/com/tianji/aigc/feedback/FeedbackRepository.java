package com.tianji.aigc.feedback;

import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Upsert-per-message feedback store (same user+message updates the previous rating). */
@Repository
public class FeedbackRepository {

    private final ConcurrentMap<String, FeedbackRecord> byMessageId = new ConcurrentHashMap<>();

    public FeedbackRecord upsertByMessage(FeedbackRecord record) {
        FeedbackRecord previous = byMessageId.put(record.messageId(), record);
        return previous == null ? record : previous;
    }

    public Optional<FeedbackRecord> find(String messageId) {
        return Optional.ofNullable(byMessageId.get(messageId));
    }

    public int size() {
        return byMessageId.size();
    }
}
