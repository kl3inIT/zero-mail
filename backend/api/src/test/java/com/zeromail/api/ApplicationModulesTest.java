package com.zeromail.api;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ApplicationModulesTest {

    @Test
    void verify() {
        ApplicationModules.of(Application.class).verify();
    }
}
