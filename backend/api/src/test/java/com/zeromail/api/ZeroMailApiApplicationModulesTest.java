package com.zeromail.api;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ZeroMailApiApplicationModulesTest {

    @Test
    void verify() {
        ApplicationModules.of(ZeroMailApiApplication.class).verify();
    }
}
