package com.ehsaniara.espagination.configuration;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jay Ehsaniara
 * https://github.com/ehsaniara
 */
@Configuration
@Slf4j
public class ElasticsearchConfiguration {

    @Value("${elasticsearch.scheme}")
    public String scheme;

    @Value("${elasticsearch.host}")
    public String host;

    @Value("${elasticsearch.port}")
    public Integer port;


    @SneakyThrows
    @Bean
    public RestHighLevelClient getRestHighLevelClient() {

        return new RestHighLevelClient(RestClient.builder(new HttpHost(host, port, scheme))
                .setHttpClientConfigCallback(b -> b.setDefaultCredentialsProvider(new BasicCredentialsProvider())));

    }

}
