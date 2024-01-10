package org.example;

import org.example.configuration.CasConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * @author lihui
 * @since 2024/1/10
 */
@AutoConfiguration
public class CasAutoConfiguration {
    @Bean
    public CasConfiguration casConfiguration() {
        return new CasConfiguration();
    }
}