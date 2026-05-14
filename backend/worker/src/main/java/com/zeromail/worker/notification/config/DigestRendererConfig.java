package com.zeromail.worker.notification.config;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

@Configuration
public class DigestRendererConfig {

    @Bean
    public MessageSource digestMessageSource() {
        ReloadableResourceBundleMessageSource messageSource =
                new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:i18n/digest");
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setUseCodeAsDefaultMessage(false);
        return messageSource;
    }

    @Bean
    public SpringTemplateEngine digestTemplateEngine(
            @Qualifier("digestMessageSource") MessageSource digestMessageSource) {
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolvers(Set.of(htmlTemplateResolver(), textTemplateResolver()));
        templateEngine.setTemplateEngineMessageSource(digestMessageSource);
        return templateEngine;
    }

    private static ITemplateResolver htmlTemplateResolver() {
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("email-templates/digest/");
        templateResolver.setSuffix("");
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        templateResolver.setCheckExistence(true);
        templateResolver.setResolvablePatterns(Set.of("*.html.thymeleaf"));
        templateResolver.setCacheable(true);
        return templateResolver;
    }

    private static ITemplateResolver textTemplateResolver() {
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("email-templates/digest/");
        templateResolver.setSuffix("");
        templateResolver.setTemplateMode(TemplateMode.TEXT);
        templateResolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        templateResolver.setCheckExistence(true);
        templateResolver.setResolvablePatterns(Set.of("*.txt.thymeleaf"));
        templateResolver.setCacheable(true);
        return templateResolver;
    }
}
