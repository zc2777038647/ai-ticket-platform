package com.xiaoyang.aiticketplatform.config;

import com.xiaoyang.aiticketplatform.ai.AiServiceClient;
import com.xiaoyang.aiticketplatform.ai.RestClientAiServiceClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(AiServiceProperties.class)
public class AiServiceConfig {

    @Bean
    public HttpClient aiHttpClient(AiServiceProperties properties) {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.getConnectTimeout())
                .build();
    }

    @Bean
    public AiServiceClient aiServiceClient(
            HttpClient aiHttpClient,
            AiServiceProperties properties,
            ObjectMapper objectMapper
    ) {
        return new RestClientAiServiceClient(aiHttpClient, properties, objectMapper);
    }
}
