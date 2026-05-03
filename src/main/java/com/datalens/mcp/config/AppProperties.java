package com.datalens.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "datalens")
public record AppProperties(Security security, Demo demo) {

    public record Security(
            int maxRows,
            int queryTimeoutSec,
            int maxQueryLength,
            boolean allowWrite
    ) {}

    public record Demo(boolean enabled) {}
}
