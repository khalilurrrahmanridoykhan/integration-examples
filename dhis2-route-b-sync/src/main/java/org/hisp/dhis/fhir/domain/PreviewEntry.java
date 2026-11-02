package org.hisp.dhis.fhir.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// Same shape as SyncLog (minus id/timestamp/direction/syncType, which the caller already
// knows from which preview endpoint it hit) -- a preview entry describes exactly what
// SyncLog entry WOULD be written if this were a real (non-preview) run, without writing
// it or touching DHIS2/FHIR.
@Getter
@RequiredArgsConstructor
public class PreviewEntry
{
    private final String sourceId;
    private final String action;
    private final String message;
}
