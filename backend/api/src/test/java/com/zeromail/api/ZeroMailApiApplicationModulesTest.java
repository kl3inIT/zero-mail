package com.zeromail.api;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ZeroMailApiApplicationModulesTest {

    @Test
    void verifyAndDocument() {
        ApplicationModules modules = ApplicationModules.of(ZeroMailApiApplication.class);
        modules.verify();

        new Documenter(modules).writeDocumentation();
    }
}
