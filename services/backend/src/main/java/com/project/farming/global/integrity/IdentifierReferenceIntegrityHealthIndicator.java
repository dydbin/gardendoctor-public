package com.project.farming.global.integrity;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.integrity.identifier-reference.health",
        name = "enabled",
        havingValue = "true"
)
public class IdentifierReferenceIntegrityHealthIndicator implements HealthIndicator {

    private final IdentifierReferenceIntegrityService integrityService;

    @Override
    public Health health() {
        IdentifierReferenceIntegrityReport report = integrityService.inspect();
        Health.Builder builder = report.isClean() ? Health.up() : Health.down();
        builder.withDetail("checkedReferenceCount", report.checkedReferenceCount());
        builder.withDetail("totalOrphanCount", report.totalOrphanCount());
        if (!report.isClean()) {
            builder.withDetail("orphanCounts", orphanCounts(report));
        }
        return builder.build();
    }

    private Map<String, Long> orphanCounts(IdentifierReferenceIntegrityReport report) {
        return report.orphanedReferences().stream()
                .collect(Collectors.toMap(
                        IdentifierReferenceOrphan::referenceName,
                        IdentifierReferenceOrphan::orphanCount));
    }
}
