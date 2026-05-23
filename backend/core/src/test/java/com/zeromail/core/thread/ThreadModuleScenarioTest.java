package com.zeromail.core.thread;

import com.zeromail.core.ZeroMailCoreModuleTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ThreadModuleScenarioTest {

    @Test
    void core_application_modules_verify_without_boundary_violations() {
        ApplicationModules.of(ZeroMailCoreModuleTestApplication.class).verify();
    }
}
