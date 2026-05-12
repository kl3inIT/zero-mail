package com.zeromail.api.controllers.thread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.zeromail.api.config.GlobalExceptionHandler;
import com.zeromail.api.error.ErrorCodes;
import com.zeromail.api.error.InvalidCursorException;
import com.zeromail.core.draft.exception.DraftGenerationFailedException;
import com.zeromail.core.draft.exception.DraftGenerationInFlightException;
import com.zeromail.core.llm.exception.SafetyViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class DraftLockContentionTest {

    private static final String GENERATE_THREAD_DRAFT_SERVICE =
            "com.zeromail.core.draft.usecases.GenerateThreadDraftService";
    private static final String ERROR_CODES = "com.zeromail.api.error.ErrorCodes";

    @Test
    void second_concurrent_draft_request_returns_http_409_in_flight_code() {
        assertFutureTypePresent(GENERATE_THREAD_DRAFT_SERVICE);
        assertFutureTypePresent(ERROR_CODES);
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ProblemDetail> response =
                handler.onDraftGenerationInFlight(new DraftGenerationInFlightException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties())
                .containsEntry("code", ErrorCodes.DRAFT_GENERATION_IN_FLIGHT);
    }

    @Test
    void draft_generation_and_cursor_errors_have_specific_status_codes() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ProblemDetail> failedResponse =
                handler.onDraftGenerationFailed(new DraftGenerationFailedException());
        ResponseEntity<ProblemDetail> cursorResponse =
                handler.onInvalidCursor(new InvalidCursorException(new IllegalArgumentException()));
        ResponseEntity<ProblemDetail> safetyResponse =
                handler.onSafetyViolation(new SafetyViolationException());

        assertThat(failedResponse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(failedResponse.getBody()).isNotNull();
        assertThat(failedResponse.getBody().getProperties())
                .containsEntry("code", ErrorCodes.DRAFT_GENERATION_FAILED);
        assertThat(cursorResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(cursorResponse.getBody()).isNotNull();
        assertThat(cursorResponse.getBody().getProperties())
                .containsEntry("code", ErrorCodes.INVALID_CURSOR);
        assertThat(safetyResponse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(safetyResponse.getBody()).isNotNull();
        assertThat(safetyResponse.getBody().getProperties())
                .containsEntry("code", ErrorCodes.LLM_SAFETY_VIOLATION);
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }
}
