package com.zeromail.core.config;

import java.net.http.HttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class RestClientConfig {

    /**
     * Default outbound HTTP client. HTTPS uses HTTP/2 via ALPN automatically. Marked {@link
     * Primary} so Spring AI auto-config (DeepSeek/OpenAI/Anthropic chat models) that autowires a
     * bare {@code RestClient.Builder} resolves to this builder rather than failing with a {@code
     * NoUniqueBeanDefinitionException} when {@link #cleartextRestClientBuilder} is also on the
     * classpath.
     */
    @Bean
    @Primary
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

    /**
     * HTTP/1.1-only client for use against backends that do not support h2c (HTTP/2 cleartext
     * upgrade). The JDK HttpClient otherwise attempts h2c on any {@code http://} URL, which hangs
     * against Node.js servers like 9router that do not advertise SETTINGS frames or honor {@code
     * Upgrade: h2c}. HTTPS still negotiates HTTP/2 via ALPN when callers use this client against an
     * {@code https://} URL, so callers should pick the default builder when the scheme is {@code
     * https}.
     */
    @Bean("cleartextRestClientBuilder")
    RestClient.Builder cleartextRestClientBuilder(ZeroMailCoreProperties zeroMailCoreProperties) {
        ZeroMailCoreProperties.ZeroMailLlmByokProperties byokProperties =
                zeroMailCoreProperties.llm().byok();
        HttpClient httpClient =
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(byokProperties.connectTimeout())
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(byokProperties.readTimeout());
        return RestClient.builder().requestFactory(requestFactory);
    }
}
