package com.datalens.mcp.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StartupHealthLogger {

    private static final Logger log = LoggerFactory.getLogger(StartupHealthLogger.class);

    private final ConnectionHealthIndicator healthIndicator;

    public StartupHealthLogger(ConnectionHealthIndicator healthIndicator) {
        this.healthIndicator = healthIndicator;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Health health = healthIndicator.health();
        if (health.getStatus() == Status.UP) {
            log.info("datalens-mcp started — health: UP {}", health.getDetails());
        } else {
            log.warn("datalens-mcp started — health: DOWN {}", health.getDetails());
        }
    }
}
