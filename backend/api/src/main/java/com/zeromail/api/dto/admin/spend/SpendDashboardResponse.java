package com.zeromail.api.dto.admin.spend;

import com.zeromail.core.admin.spend.projection.SpendDashboardSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(
        requiredProperties = {
            "kpis",
            "stackBar",
            "donut",
            "topTenants",
            "snapshotAt",
            "kAnonymityFooterNote",
            "unknownPercentOfTotal",
            "rowLevelClassificationSince"
        })
public record SpendDashboardResponse(
        SpendKpiResponse kpis,
        List<ProviderStackBarRowResponse> stackBar,
        List<FeatureDonutSliceResponse> donut,
        List<TopTenantRowResponse> topTenants,
        Instant snapshotAt,
        String kAnonymityFooterNote,
        double unknownPercentOfTotal,
        String rowLevelClassificationSince) {

    public SpendDashboardResponse {
        stackBar = List.copyOf(stackBar);
        donut = List.copyOf(donut);
        topTenants = List.copyOf(topTenants);
    }

    public static SpendDashboardResponse from(
            SpendDashboardSnapshot snapshot, String rowLevelClassificationSince) {
        return new SpendDashboardResponse(
                SpendKpiResponse.from(snapshot.kpis()),
                snapshot.stackBar().stream().map(ProviderStackBarRowResponse::from).toList(),
                snapshot.donut().stream().map(FeatureDonutSliceResponse::from).toList(),
                snapshot.topTenants().stream().map(TopTenantRowResponse::from).toList(),
                snapshot.snapshotAt(),
                snapshot.kAnonymityFooterNote(),
                snapshot.unknownPercentOfTotal(),
                rowLevelClassificationSince);
    }
}
