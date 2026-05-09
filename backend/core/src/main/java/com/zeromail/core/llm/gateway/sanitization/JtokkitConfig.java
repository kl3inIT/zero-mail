package com.zeromail.core.llm.gateway.sanitization;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.EncodingRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JtokkitConfig {

    @Bean
    EncodingRegistry encodingRegistry() {
        return Encodings.newLazyEncodingRegistry();
    }
}
