package com.project.farming.global.integrity;

import java.util.List;
import java.util.Objects;

public record IdentifierReferenceIntegrityReport(
        List<IdentifierReferenceOrphan> references
) {
    public IdentifierReferenceIntegrityReport {
        references = List.copyOf(Objects.requireNonNull(references, "references"));
    }

    public int checkedReferenceCount() {
        return references.size();
    }

    public long totalOrphanCount() {
        return references.stream()
                .mapToLong(IdentifierReferenceOrphan::orphanCount)
                .sum();
    }

    public List<IdentifierReferenceOrphan> orphanedReferences() {
        return references.stream()
                .filter(IdentifierReferenceOrphan::hasOrphans)
                .toList();
    }

    public boolean isClean() {
        return totalOrphanCount() == 0;
    }
}
