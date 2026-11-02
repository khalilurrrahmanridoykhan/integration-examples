package org.hisp.dhis.fhir;

import lombok.RequiredArgsConstructor;
import org.apache.camel.ProducerTemplate;
import org.hisp.dhis.fhir.config.ScheduleGate;
import org.hisp.dhis.fhir.config.SyncTypes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Manual trigger for every sync route, alongside each one's own schedule (see
// ScheduleController/ScheduleGate) -- each direct: endpoint runs the exact same route the
// timer fires (see the routes' own comments). Renamed from the original
// "dhis2-to-fhir"/"fhir-to-dhis2" (Immunization-only) keys once org-units/option-sets/
// tracked-entities were added, since those names were ambiguous once more than one
// resource type existed per direction.
@RestController
@RequestMapping( "/api/sync" )
@RequiredArgsConstructor
public class SyncTriggerController
{
    private final ProducerTemplate producerTemplate;

    private final ScheduleGate scheduleGate;

    @PostMapping( "/{type}" )
    public ResponseEntity<?> trigger( @PathVariable String type )
    {
        String endpoint = SyncTypes.DIRECT_ENDPOINT_BY_TYPE.get( type );
        if ( endpoint == null )
        {
            return ResponseEntity.badRequest()
                .body( Map.of( "error", "Unknown sync type '" + type + "', expected one of "
                    + SyncTypes.DIRECT_ENDPOINT_BY_TYPE.keySet() ) );
        }

        producerTemplate.sendBody( endpoint, null );
        // Resets the automatic schedule's clock -- see ScheduleGate.markRanNow's own
        // comment on why a manual run shouldn't leave an automatic one due moments later.
        scheduleGate.markRanNow( type );
        return ResponseEntity.ok( Map.of( "status", "triggered", "type", type ) );
    }
}
