package com.project.farming.global.integrity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.integrity.identifier-reference.monitor",
        name = "enabled",
        havingValue = "true"
)
public class IdentifierReferenceIntegrityMonitor {

    private final IdentifierReferenceIntegrityService integrityService;

    @Scheduled(cron = "${app.integrity.identifier-reference.monitor.cron:0 0 3 * * *}")
    public void inspectIdentifierReferences() {
        IdentifierReferenceIntegrityReport report = integrityService.inspect();
        if (report.isClean()) {
            log.debug(
                    "Identifier reference integrity check passed. checkedReferenceCount={}",
                    report.checkedReferenceCount());
            return;
        }

        String orphanSummary = report.orphanedReferences().stream()
                .map(orphan -> orphan.referenceName() + "=" + orphan.orphanCount())
                .collect(Collectors.joining(", "));
        log.warn(
                "Identifier reference integrity check found orphan rows. checkedReferenceCount={}, totalOrphanCount={}, orphanCounts={}",
                report.checkedReferenceCount(),
                report.totalOrphanCount(),
                orphanSummary);
    }
}
