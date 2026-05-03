package com.datalens.mcp;

import com.datalens.mcp.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(AppProperties.class)
public class DataLensMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataLensMcpApplication.class, args);
    }
}
