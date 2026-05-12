package com.zeromail.core.config;

import java.net.http.HttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class RestClientConfig {

    @Bean
    @ConditionalOnMissingBean(RestClient.Builder.class)
    RestClient.Builder restClientBuilder(ZeroMailCoreProperties zeroMailCoreProperties) {
        ZeroMailCoreProperties.ZeroMailLlmByokProperties byokProperties =
                zeroMailCoreProperties.llm().byok();
        HttpClient httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(byokProperties.connectTimeout())
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(byokProperties.readTimeout());
        return RestClient.builder().requestFactory(requestFactory);
    }
}
