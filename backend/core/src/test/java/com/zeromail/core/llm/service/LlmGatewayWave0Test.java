package com.zeromail.core.llm.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Plan 03 lands LlmGateway")
class LlmGatewayWave0Test {

    @Test
    void exposes_gateway_contract_for_downstream_rules_and_triage() {
        assertThat(LlmGateway.class.getName()).isEqualTo("com.zeromail.core.llm.service.LlmGateway");
    }
}
