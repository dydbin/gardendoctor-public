package com.project.farming.global.integrity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdentifierReferenceIntegrityMonitorTest {

    @Test
    void monitorShouldInspectReferencesWithoutCleanupSideEffects() {
        IdentifierReferenceIntegrityService integrityService = mock(IdentifierReferenceIntegrityService.class);
        when(integrityService.inspect()).thenReturn(new IdentifierReferenceIntegrityReport(List.of(
                new IdentifierReferenceOrphan("users.profile_image_file_id", 0)
        )));
        IdentifierReferenceIntegrityMonitor monitor = new IdentifierReferenceIntegrityMonitor(integrityService);

        monitor.inspectIdentifierReferences();

        verify(integrityService).inspect();
    }

    @Test
    void monitorShouldStillOnlyInspectWhenOrphansExist() {
        IdentifierReferenceIntegrityService integrityService = mock(IdentifierReferenceIntegrityService.class);
        when(integrityService.inspect()).thenReturn(new IdentifierReferenceIntegrityReport(List.of(
                new IdentifierReferenceOrphan("diaries.user_id", 2)
        )));
        IdentifierReferenceIntegrityMonitor monitor = new IdentifierReferenceIntegrityMonitor(integrityService);

        monitor.inspectIdentifierReferences();

        verify(integrityService).inspect();
    }
}
