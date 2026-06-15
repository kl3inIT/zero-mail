package com.zeromail.api.dto.admin.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class TenantActivityEventResponseTest {

    @Test
    void response_contract_does_not_expose_ip_location_or_device_fields() {
        assertThat(
                        Arrays.stream(TenantActivityEventResponse.class.getRecordComponents())
                                .map(RecordComponent::getName))
                .doesNotContain("ipAddress", "locationLabel", "deviceFamily", "userAgent");
    }
}
