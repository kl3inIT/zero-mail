package com.zeromail.api.debug;

import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.concurrency.TenantAwareTaskScope;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;
import java.util.stream.IntStream;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("test")
public class DebugController {

    @GetMapping("/debug/tenant-echo")
    public String tenantEcho() {
        return TenantContext.currentOrThrow();
    }

    @GetMapping("/debug/fanout-echo")
    public List<String> fanoutEcho(@RequestParam(defaultValue = "10") int n) throws Exception {
        try (var scope = TenantAwareTaskScope.openInherit()) {
            var subs =
                    IntStream.range(0, n)
                            .mapToObj(i -> scope.<String>fork(TenantContext::currentOrThrow))
                            .toList();
            scope.join();
            return subs.stream().map(StructuredTaskScope.Subtask::get).toList();
        }
    }
}
