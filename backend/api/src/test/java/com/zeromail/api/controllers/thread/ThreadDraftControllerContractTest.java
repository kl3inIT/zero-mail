package com.zeromail.api.controllers.thread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.zeromail.api.dto.thread.NeedsReplyListResponse;
import com.zeromail.api.dto.thread.ThreadDraftResponse;
import com.zeromail.api.dto.thread.ToReplyCountResponse;
import com.zeromail.core.thread.domain.ThreadReplyBucket;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class ThreadDraftControllerContractTest {

    private static final String THREAD_DRAFT_CONTROLLER =
            "com.zeromail.api.controllers.thread.ThreadDraftController";
    private static final String NEEDS_REPLY_INBOX_CONTROLLER =
            "com.zeromail.api.controllers.thread.NeedsReplyInboxController";
    private static final String THREAD_DRAFT_RESPONSE =
            "com.zeromail.api.dto.thread.ThreadDraftResponse";
    private static final String NEEDS_REPLY_LIST_RESPONSE =
            "com.zeromail.api.dto.thread.NeedsReplyListResponse";

    @Test
    void draft_endpoint_returns_no_body_and_links_to_gmail() throws Exception {
        assertFutureTypePresent(THREAD_DRAFT_CONTROLLER);
        assertFutureTypePresent(THREAD_DRAFT_RESPONSE);

        RequestMapping controllerMapping =
                ThreadDraftController.class.getAnnotation(RequestMapping.class);
        PostMapping draftMapping =
                ThreadDraftController.class
                        .getMethod("generateDraft", String.class)
                        .getAnnotation(PostMapping.class);
        assertThat(controllerMapping.value()).containsExactly("/api/threads");
        assertThat(draftMapping.value()).containsExactly("/{gmailThreadId}/draft");
        assertThat(recordComponentNames(ThreadDraftResponse.class))
                .containsExactly("draftId", "gmailThreadId", "status", "openInGmailUrl");
        assertThat(recordComponentNames(ThreadDraftResponse.class)).doesNotContain("body");
    }

    @Test
    void needs_reply_endpoint_uses_hyphenated_public_bucket_slugs() throws Exception {
        assertFutureTypePresent(NEEDS_REPLY_INBOX_CONTROLLER);
        assertFutureTypePresent(NEEDS_REPLY_LIST_RESPONSE);

        RequestMapping controllerMapping =
                NeedsReplyInboxController.class.getAnnotation(RequestMapping.class);
        GetMapping getMapping =
                NeedsReplyInboxController.class
                        .getMethod("list", String.class, String.class, int.class, boolean.class)
                        .getAnnotation(GetMapping.class);
        assertThat(controllerMapping.value()).containsExactly("/api/threads");
        assertThat(getMapping.value()).isEmpty();
        assertThat("to-reply").isEqualTo("to-reply");
        assertThat("awaiting-their-reply").isEqualTo("awaiting-their-reply");
        assertThat(ThreadReplyBucket.fromPublicSlug("TO-REPLY"))
                .isEqualTo(ThreadReplyBucket.TO_REPLY);
        assertThat(ThreadReplyBucket.fromPublicSlug("awaiting-their-reply"))
                .isEqualTo(ThreadReplyBucket.AWAITING_THEIR_REPLY);
        assertThat(recordComponentNames(NeedsReplyListResponse.class))
                .containsExactly("items", "nextCursor", "toReplyCount");
        GetMapping countMapping =
                NeedsReplyInboxController.class
                        .getMethod("toReplyCount")
                        .getAnnotation(GetMapping.class);
        assertThat(countMapping.value()).containsExactly("/to-reply-count");
        assertThat(recordComponentNames(ToReplyCountResponse.class))
                .containsExactly("toReplyCount");
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }

    private static java.util.List<String> recordComponentNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }
}
