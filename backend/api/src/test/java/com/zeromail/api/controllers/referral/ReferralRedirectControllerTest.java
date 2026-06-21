package com.zeromail.api.controllers.referral;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

class ReferralRedirectControllerTest {

    @Test
    void acceptReferralSupportsProductionApiProxyPath() throws Exception {
        Method acceptReferralMethod =
                ReferralRedirectController.class.getDeclaredMethod(
                        "acceptReferral",
                        String.class,
                        HttpServletRequest.class,
                        HttpServletResponse.class);

        GetMapping getMapping = acceptReferralMethod.getAnnotation(GetMapping.class);

        assertThat(getMapping.value()).contains("/r/{code}", "/api/r/{code}");
    }
}
