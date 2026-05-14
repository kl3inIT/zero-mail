package com.zeromail.core.thread.persistence;

import com.zeromail.core.thread.domain.ThreadReplyBucket;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ThreadReplyBucketAttributeConverter
        implements AttributeConverter<ThreadReplyBucket, String> {

    @Override
    public String convertToDatabaseColumn(ThreadReplyBucket bucket) {
        return bucket == null ? null : bucket.id();
    }

    @Override
    public ThreadReplyBucket convertToEntityAttribute(String databaseColumn) {
        return databaseColumn == null ? null : ThreadReplyBucket.fromId(databaseColumn);
    }
}
