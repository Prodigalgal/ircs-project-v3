package com.prodigalgal.ircs.aggregation;

import java.util.UUID;

record AuthorityCatalogBindingDecision(String status, UUID catalogEntryId, String evidenceKind) {
    static AuthorityCatalogBindingDecision bind(UUID catalogEntryId, String evidenceKind) {
        return new AuthorityCatalogBindingDecision("BOUND", catalogEntryId, evidenceKind);
    }

    static AuthorityCatalogBindingDecision waitForAuthority() {
        return new AuthorityCatalogBindingDecision("WAITING_AUTHORITY", null, null);
    }

    static AuthorityCatalogBindingDecision review(String status) {
        return new AuthorityCatalogBindingDecision(status, null, null);
    }
}
