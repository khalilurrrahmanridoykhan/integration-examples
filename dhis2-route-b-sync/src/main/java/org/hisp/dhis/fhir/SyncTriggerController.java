package org.hisp.dhis.fhir;

import lombok.RequiredArgsConstructor;
import org.apache.camel.ProducerTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Manual trigger for the two Immunization sync routes, alongside their hourly schedule --
// each direct: endpoint runs the exact same route the timer fires (see the routes' comments).
@RestController
@RequestMapping( "/api/sync" )
@RequiredArgsConstructor
public class SyncTriggerController
{
    private final ProducerTemplate producerTemplate;

    private static final Map<String, String> DIRECT_ENDPOINT_BY_TYPE = Map.of(
        "dhis2-to-fhir", "direct:dhis2ToFhirSync",
        "fhir-to-dhis2", "direct:fhirToDhis2Sync"
    );

    @PostMapping( "/{type}" )
    public ResponseEntity<?> trigger( @PathVariable String type )
    {
        String endpoint = DIRECT_ENDPOINT_BY_TYPE.get( type );
        if ( endpoint == null )
        {
            return ResponseEntity.badRequest()
                .body( Map.of( "error", "Unknown sync type '" + type + "', expected one of "
                    + DIRECT_ENDPOINT_BY_TYPE.keySet() ) );
        }

        producerTemplate.sendBody( endpoint, null );
        return ResponseEntity.ok( Map.of( "status", "triggered", "type", type ) );
    }
}
