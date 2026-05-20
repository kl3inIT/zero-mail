package com.zeromail.api.dto.admin.spend;

import com.zeromail.core.admin.spend.projection.SpendKpis;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(
        requiredProperties = {
            "todayPlatformCost",
            "todayByokCost",
            "todayUnknownCost",
            "sevenDayPlatformCost",
            "sevenDayByokCost",
            "sevenDayUnknownCost",
            "thirtyDayPlatformCost",
            "thirtyDayByokCost",
            "thirtyDayUnknownCost",
            "todayCallCount",
            "sevenDayCallCount",
            "thirtyDayCallCount"
        })
public record SpendKpiResponse(
        BigDecimal todayPlatformCost,
        BigDecimal todayByokCost,
        BigDecimal todayUnknownCost,
        BigDecimal sevenDayPlatformCost,
        BigDecimal sevenDayByokCost,
        BigDecimal sevenDayUnknownCost,
        BigDecimal thirtyDayPlatformCost,
        BigDecimal thirtyDayByokCost,
        BigDecimal thirtyDayUnknownCost,
        int todayCallCount,
        int sevenDayCallCount,
        int thirtyDayCallCount) {

    public static SpendKpiResponse from(SpendKpis kpis) {
        return new SpendKpiResponse(
                kpis.todayPlatformCost(),
                kpis.todayByokCost(),
                kpis.todayUnknownCost(),
                kpis.sevenDayPlatformCost(),
                kpis.sevenDayByokCost(),
                kpis.sevenDayUnknownCost(),
                kpis.thirtyDayPlatformCost(),
                kpis.thirtyDayByokCost(),
                kpis.thirtyDayUnknownCost(),
                kpis.todayCallCount(),
                kpis.sevenDayCallCount(),
                kpis.thirtyDayCallCount());
    }
}
