package com.datalens.mcp.health;

import com.datalens.mcp.model.ConnectionConfig;
import com.datalens.mcp.registry.ConnectionRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ConnectionHealthIndicator implements HealthIndicator {

    private final ConnectionRegistry registry;

    public ConnectionHealthIndicator(ConnectionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Health health() {
        List<ConnectionConfig> all = registry.listAll();

        if (all.isEmpty()) {
            return Health.up()
                    .withDetail("connections", "none registered")
                    .build();
        }

        Map<String, String> statuses = new LinkedHashMap<>();
        boolean anyDown = false;

        for (ConnectionConfig config : all) {
            boolean ok = registry.find(config.id())
                    .map(e -> {
                        try {
                            return e.adapter().testConnection();
                        } catch (Exception ex) {
                            return false;
                        }
                    })
                    .orElse(false);

            statuses.put(config.id(), ok ? "UP" : "DOWN");
            if (!ok) anyDown = true;
        }

        Health.Builder builder = anyDown ? Health.down() : Health.up();
        statuses.forEach(builder::withDetail);
        return builder.build();
    }
}
