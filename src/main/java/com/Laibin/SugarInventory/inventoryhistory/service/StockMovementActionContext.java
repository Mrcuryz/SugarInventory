package com.Laibin.SugarInventory.inventoryhistory.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
public class StockMovementActionContext {
    private final ThreadLocal<ActionState> current = new ThreadLocal<>();

    public Scope open(String actionKind, String preferredBusinessActionId) {
        ActionState existing = current.get();
        if (existing != null) {
            return new Scope(false);
        }
        current.set(new ActionState(
                normalizeBusinessActionId(preferredBusinessActionId),
                normalizeActionKind(actionKind),
                LocalDateTime.now()
        ));
        return new Scope(true);
    }

    public Optional<ActionState> current() {
        return Optional.ofNullable(current.get());
    }

    private String normalizeBusinessActionId(String preferred) {
        if (preferred == null || preferred.isBlank()) {
            return "act_" + UUID.randomUUID().toString().replace("-", "");
        }
        String normalized = preferred.trim().replaceAll("[^A-Za-z0-9_.:-]", "_");
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80);
    }

    private String normalizeActionKind(String actionKind) {
        if (actionKind == null || actionKind.isBlank()) {
            return "UNSPECIFIED";
        }
        String normalized = actionKind.trim().toUpperCase();
        return normalized.length() <= 48 ? normalized : normalized.substring(0, 48);
    }

    public record ActionState(
            String businessActionId,
            String actionKind,
            LocalDateTime occurredAt
    ) {
    }

    public final class Scope implements AutoCloseable {
        private final boolean owner;
        private boolean closed;

        private Scope(boolean owner) {
            this.owner = owner;
        }

        @Override
        public void close() {
            if (owner && !closed) {
                current.remove();
                closed = true;
            }
        }
    }
}
