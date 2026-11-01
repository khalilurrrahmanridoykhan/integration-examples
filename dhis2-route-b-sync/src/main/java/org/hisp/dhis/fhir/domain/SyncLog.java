package org.hisp.dhis.fhir.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class SyncLog
{
    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    private Long id;

    // "immunization", "org-unit", "option-set", or "tracked-entity" -- which of the five
    // sync routes wrote this entry. Needed because "direction" alone can't tell an
    // Immunization entry apart from a metadata one: both use "dhis2-to-fhir" (see below),
    // so the console's separate Immunization/Metadata tabs filter history on this field.
    private String syncType;

    // "dhis2-to-fhir" (Route B) or "fhir-to-dhis2" (Route A)
    private String direction;

    // the DHIS2 event id or FHIR Immunization id this run was triggered by
    private String sourceId;

    // "created", "updated", "skipped-not-mapped", "skipped-own-output", "skipped-not-bcg"
    private String action;

    private String message;

    private Instant timestamp;

    public SyncLog( String syncType, String direction, String sourceId, String action, String message )
    {
        this.syncType = syncType;
        this.direction = direction;
        this.sourceId = sourceId;
        this.action = action;
        this.message = message;
        this.timestamp = Instant.now();
    }
}
