package org.hisp.dhis.fhir.config;

import lombok.RequiredArgsConstructor;
import org.hisp.dhis.fhir.domain.SyncSchedule;
import org.hisp.dhis.fhir.repository.SyncScheduleRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

// Called from each route's "...Tick" timer (period=300000, 5 minutes -- see e.g.
// VaccinationEventToFhirImmunizationRoute) to decide whether that tick should actually run
// the sync. Deliberately NOT a Camel-rebuilt-timer-period approach: Camel's timer:// period
// is fixed at route-build time, so reconfiguring it at runtime would mean stopping and
// rebuilding the route itself. A frequent, cheap tick that mostly no-ops is simpler and
// safer than route lifecycle manipulation for what's fundamentally an hours-granularity
// schedule.
@Component
@RequiredArgsConstructor
public class ScheduleGate
{
    private static final int DEFAULT_INTERVAL_HOURS = 1;

    private final SyncScheduleRepository syncScheduleRepository;

    // Every call that returns true also stamps lastRunAt as a side effect -- the route
    // only calls this once per tick, right before (and gating) its own direct: route, so
    // "decided yes" and "about to run" are the same moment.
    public boolean shouldRunNow( String syncType )
    {
        SyncSchedule schedule = syncScheduleRepository.findById( syncType )
            .orElseGet( () -> new SyncSchedule( syncType, "AUTOMATIC", DEFAULT_INTERVAL_HOURS ) );

        if ( !"AUTOMATIC".equals( schedule.getMode() ) )
        {
            return false;
        }

        Instant now = Instant.now();
        boolean due = schedule.getLastRunAt() == null
            || !now.isBefore( schedule.getLastRunAt().plus( schedule.getIntervalHours(), ChronoUnit.HOURS ) );
        if ( !due )
        {
            return false;
        }

        schedule.setLastRunAt( now );
        syncScheduleRepository.save( schedule );
        return true;
    }

    // Called by the manual-trigger and one-time-run endpoints too, so an admin-initiated
    // run resets the automatic clock -- without this, a manual run moments before an
    // automatic one was due wouldn't stop that automatic run from firing right after.
    public void markRanNow( String syncType )
    {
        SyncSchedule schedule = syncScheduleRepository.findById( syncType )
            .orElseGet( () -> new SyncSchedule( syncType, "AUTOMATIC", DEFAULT_INTERVAL_HOURS ) );
        schedule.setLastRunAt( Instant.now() );
        syncScheduleRepository.save( schedule );
    }
}
