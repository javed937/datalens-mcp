package com.datalens.mcp.registry;

import com.datalens.mcp.adapter.DatabaseAdapter;
import com.datalens.mcp.model.ConnectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConnectionRegistry.class);

    public record ConnectionEntry(ConnectionConfig config, DatabaseAdapter adapter) {}

    private final ConcurrentHashMap<String, ConnectionEntry> connections = new ConcurrentHashMap<>();

    public void register(ConnectionConfig config, DatabaseAdapter adapter) {
        connections.put(config.id(), new ConnectionEntry(config, adapter));
        log.info("Registered connection '{}' (type={})", config.id(), config.type());
    }

    public Optional<ConnectionEntry> find(String id) {
        return Optional.ofNullable(connections.get(id));
    }

    public List<ConnectionConfig> listAll() {
        return connections.values().stream()
                .map(ConnectionEntry::config)
                .toList();
    }

    public boolean remove(String id) {
        ConnectionEntry removed = connections.remove(id);
        if (removed != null) {
            removed.adapter().close();
            log.info("Removed connection '{}'", id);
            return true;
        }
        return false;
    }

    public boolean exists(String id) {
        return connections.containsKey(id);
    }
}
