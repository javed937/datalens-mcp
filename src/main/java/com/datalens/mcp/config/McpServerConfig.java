package com.datalens.mcp.config;

import com.datalens.mcp.tools.ConnectionTools;
import com.datalens.mcp.tools.ExplainTools;
import com.datalens.mcp.tools.ExportTools;
import com.datalens.mcp.tools.QueryTools;
import com.datalens.mcp.tools.SchemaTools;
import com.datalens.mcp.tools.StatsTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public ToolCallbackProvider connectionToolProvider(ConnectionTools connectionTools) {
        return MethodToolCallbackProvider.builder().toolObjects(connectionTools).build();
    }

    @Bean
    public ToolCallbackProvider queryToolProvider(QueryTools queryTools) {
        return MethodToolCallbackProvider.builder().toolObjects(queryTools).build();
    }

    @Bean
    public ToolCallbackProvider schemaToolProvider(SchemaTools schemaTools) {
        return MethodToolCallbackProvider.builder().toolObjects(schemaTools).build();
    }

    @Bean
    public ToolCallbackProvider statsToolProvider(StatsTools statsTools) {
        return MethodToolCallbackProvider.builder().toolObjects(statsTools).build();
    }

    @Bean
    public ToolCallbackProvider explainToolProvider(ExplainTools explainTools) {
        return MethodToolCallbackProvider.builder().toolObjects(explainTools).build();
    }

    @Bean
    public ToolCallbackProvider exportToolProvider(ExportTools exportTools) {
        return MethodToolCallbackProvider.builder().toolObjects(exportTools).build();
    }
}
