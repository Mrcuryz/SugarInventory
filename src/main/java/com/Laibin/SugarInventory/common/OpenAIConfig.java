package com.Laibin.SugarInventory.common;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;
import java.net.Proxy;

@Configuration
public class OpenAIConfig {

    /**
     * 从配置文件读取 OpenAI API Key
     * application.yml:
     *   openai:
     *     api-key: sk-xxx
     */
    @Bean
    public OpenAIClient openAIClient(@Value("${openai.api-key}") String apiKey, @Value("${openai.api-url}") String apiUrl) {
        Proxy proxy = new Proxy(
                Proxy.Type.HTTP,
                new InetSocketAddress("127.0.0.1", 7890)
        );

        return OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(apiUrl)
                .proxy(proxy)
                .build();
    }
}
