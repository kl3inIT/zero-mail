package com.zeromail.api.dto.settings;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(requiredProperties = {"usd"})
public record AiCostResponse(BigDecimal usd) {}
