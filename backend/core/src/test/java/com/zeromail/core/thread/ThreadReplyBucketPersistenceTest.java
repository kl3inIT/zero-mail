package com.zeromail.core.thread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.thread.domain.ThreadReplyBucket;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class ThreadReplyBucketPersistenceTest {

    @Test
    void bucket_ids_match_check_constraint_literals() {
        assertThat(ThreadReplyBucket.values())
                .extracting(ThreadReplyBucket::id)
                .containsExactly("TO_REPLY", "AWAITING_THEIR_REPLY", "FYI", "ACTIONED");
        assertThat(ThreadReplyBucket.values())
                .allSatisfy(bucket -> assertThat(bucket.id()).isEqualTo(bucket.name()));
    }

    @Test
    void ids_and_public_slugs_round_trip_fail_loud() {
        assertThat(ThreadReplyBucket.fromId("TO_REPLY")).isEqualTo(ThreadReplyBucket.TO_REPLY);
        assertThat(ThreadReplyBucket.fromId("AWAITING_THEIR_REPLY"))
                .isEqualTo(ThreadReplyBucket.AWAITING_THEIR_REPLY);
        assertThat(ThreadReplyBucket.TO_REPLY.publicSlug()).isEqualTo("to-reply");
        assertThat(ThreadReplyBucket.AWAITING_THEIR_REPLY.publicSlug())
                .isEqualTo("awaiting-their-reply");
        assertThat(ThreadReplyBucket.fromPublicSlug("to-reply"))
                .isEqualTo(ThreadReplyBucket.TO_REPLY);
        assertThat(ThreadReplyBucket.fromPublicSlug("awaiting-their-reply"))
                .isEqualTo(ThreadReplyBucket.AWAITING_THEIR_REPLY);
        assertThat(ThreadReplyBucket.fromPublicSlug("fyi")).isEqualTo(ThreadReplyBucket.FYI);
        assertThat(ThreadReplyBucket.fromPublicSlug("actioned"))
                .isEqualTo(ThreadReplyBucket.ACTIONED);

        assertThatThrownBy(() -> ThreadReplyBucket.fromId("UNKNOWN"))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> ThreadReplyBucket.fromPublicSlug("unknown"))
                .isInstanceOf(NoSuchElementException.class);
    }
}
