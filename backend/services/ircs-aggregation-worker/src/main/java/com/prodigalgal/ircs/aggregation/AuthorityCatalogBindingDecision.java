package com.prodigalgal.ircs.aggregation;

import java.util.UUID;

record AuthorityCatalogBindingDecision(String status, UUID unifiedVideoId, String evidenceKind) {
    static AuthorityCatalogBindingDecision bind(UUID unifiedVideoId, String evidenceKind) {
        return new AuthorityCatalogBindingDecision("BOUND", unifiedVideoId, evidenceKind);
    }

    static AuthorityCatalogBindingDecision waitForAuthority() {
        return new AuthorityCatalogBindingDecision("WAITING_AUTHORITY", null, null);
    }

    static AuthorityCatalogBindingDecision review(String status) {
        return new AuthorityCatalogBindingDecision(status, null, null);
    }
}
