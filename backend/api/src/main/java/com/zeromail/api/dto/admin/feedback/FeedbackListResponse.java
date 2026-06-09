package com.zeromail.api.dto.admin.feedback;

import com.zeromail.core.support.usecases.FeedbackListQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"rows", "openCount"})
public record FeedbackListResponse(List<FeedbackRowResponse> rows, long openCount) {

    public FeedbackListResponse {
        rows = List.copyOf(rows);
    }

    public static FeedbackListResponse from(FeedbackListQuery.Result result) {
        return new FeedbackListResponse(
                result.rows().stream().map(FeedbackRowResponse::from).toList(), result.openCount());
    }
}
