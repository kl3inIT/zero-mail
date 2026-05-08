package com.zeromail.core.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
class RestClientConfig {

  @Bean
  @ConditionalOnMissingBean(RestClient.Builder.class)
  RestClient.Builder restClientBuilder() {
    return RestClient.builder();
  }
}
