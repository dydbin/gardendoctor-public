package com.project.farming.global.integrity;

public record IdentifierReferenceOrphan(
        String referenceName,
        long orphanCount
) {
    public boolean hasOrphans() {
        return orphanCount > 0;
    }
}
