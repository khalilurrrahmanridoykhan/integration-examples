package org.hisp.dhis.fhir;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.camel.ProducerTemplate;
import org.hisp.dhis.fhir.config.ScheduleGate;
import org.hisp.dhis.fhir.config.SyncTypes;
import org.hisp.dhis.fhir.domain.SyncSchedule;
import org.hisp.dhis.fhir.repository.SyncScheduleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

// Per-sync-type schedule config: AUTOMATIC (every N hours, checked by each route's own
// tick -- see ScheduleGate) or MANUAL (never auto-runs, only the trigger endpoint or a
// one-time run below), plus an optional one-time future run at a specific instant.
@RestController
@RequestMapping( "/api/schedule" )
@RequiredArgsConstructor
public class ScheduleController
{
    private static final Set<String> VALID_MODES = Set.of( "AUTOMATIC", "MANUAL" );

    private final SyncScheduleRepository syncScheduleRepository;

    private final ScheduleGate scheduleGate;

    private final TaskScheduler taskScheduler;

    private final ProducerTemplate producerTemplate;

    // One-time runs are plain in-memory ScheduledFutures (see MainConfiguration's
    // taskScheduler bean) -- lost on restart, which is an accepted limitation for this
    // feature, not an oversight.
    private final Map<String, PendingRun> pendingOneTimeRuns = new ConcurrentHashMap<>();

    @Getter
    @AllArgsConstructor
    private static class PendingRun
    {
        private final Instant runAt;
        private final ScheduledFuture<?> future;
    }

    @Getter
    @AllArgsConstructor
    private static class ScheduleView
    {
        private final String syncType;
        private final String mode;
        private final int intervalHours;
        private final Instant lastRunAt;
        private final Instant pendingOneTimeRunAt;
    }

    @Getter
    private static class UpdateScheduleRequest
    {
        private String mode;
        private Integer intervalHours;
    }

    @Getter
    private static class RunAtRequest
    {
        private Instant timestamp;
    }

    @GetMapping
    public List<ScheduleView> list()
    {
        return SyncTypes.ALL.stream().map( this::toView ).collect( Collectors.toList() );
    }

    @PutMapping( "/{type}" )
    public ResponseEntity<?> update( @PathVariable String type, @RequestBody UpdateScheduleRequest request )
    {
        if ( !SyncTypes.ALL.contains( type ) )
        {
            return ResponseEntity.badRequest().body( Map.of( "error", "Unknown sync type '" + type + "'" ) );
        }
        if ( request.getMode() == null || !VALID_MODES.contains( request.getMode() ) )
        {
            return ResponseEntity.badRequest().body( Map.of( "error", "mode must be AUTOMATIC or MANUAL" ) );
        }
        int intervalHours = request.getIntervalHours() == null ? 1 : request.getIntervalHours();
        if ( intervalHours < 1 )
        {
            return ResponseEntity.badRequest().body( Map.of( "error", "intervalHours must be at least 1" ) );
        }

        SyncSchedule schedule = syncScheduleRepository.findById( type )
            .orElseGet( () -> new SyncSchedule( type, request.getMode(), intervalHours ) );
        schedule.setMode( request.getMode() );
        schedule.setIntervalHours( intervalHours );
        syncScheduleRepository.save( schedule );

        return ResponseEntity.ok( toView( type ) );
    }

    @PostMapping( "/{type}/run-at" )
    public ResponseEntity<?> scheduleOneTimeRun( @PathVariable String type, @RequestBody RunAtRequest request )
    {
        String endpoint = SyncTypes.DIRECT_ENDPOINT_BY_TYPE.get( type );
        if ( endpoint == null )
        {
            return ResponseEntity.badRequest().body( Map.of( "error", "Unknown sync type '" + type + "'" ) );
        }
        if ( request.getTimestamp() == null || request.getTimestamp().isBefore( Instant.now() ) )
        {
            return ResponseEntity.badRequest().body( Map.of( "error", "timestamp must be in the future" ) );
        }

        PendingRun existing = pendingOneTimeRuns.get( type );
        if ( existing != null )
        {
            existing.getFuture().cancel( false );
        }

        ScheduledFuture<?> future = taskScheduler.schedule( () -> {
            producerTemplate.sendBody( endpoint, null );
            scheduleGate.markRanNow( type );
            pendingOneTimeRuns.remove( type );
        }, request.getTimestamp() );

        pendingOneTimeRuns.put( type, new PendingRun( request.getTimestamp(), future ) );

        return ResponseEntity.ok( toView( type ) );
    }

    @DeleteMapping( "/{type}/run-at" )
    public ResponseEntity<?> cancelOneTimeRun( @PathVariable String type )
    {
        PendingRun pending = pendingOneTimeRuns.remove( type );
        if ( pending != null )
        {
            pending.getFuture().cancel( false );
        }
        return ResponseEntity.ok( toView( type ) );
    }

    private ScheduleView toView( String type )
    {
        SyncSchedule schedule = syncScheduleRepository.findById( type )
            .orElseGet( () -> new SyncSchedule( type, "AUTOMATIC", 1 ) );
        PendingRun pending = pendingOneTimeRuns.get( type );
        return new ScheduleView( type, schedule.getMode(), schedule.getIntervalHours(), schedule.getLastRunAt(),
            pending == null ? null : pending.getRunAt() );
    }
}
