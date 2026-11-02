package org.hisp.dhis.fhir.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.time.Instant;

// One row per sync type (the same five keys SyncTriggerController/PreviewController use),
// upserted by ScheduleController. Absence of a row for a type means "use the default" --
// see ScheduleGate.defaultSchedule -- so every type behaves exactly like before this
// feature existed (automatic, hourly) until an admin explicitly changes it.
@Entity
@Getter
@Setter
@NoArgsConstructor
public class SyncSchedule
{
    @Id
    private String syncType;

    // "AUTOMATIC" or "MANUAL" -- MANUAL means the timer tick (see each route's
    // "...Tick" route) never fires this type; it only runs via the manual trigger
    // endpoint or a one-time scheduled run.
    private String mode;

    // Only meaningful when mode is AUTOMATIC.
    private int intervalHours;

    // When this type last actually ran (auto or manual) -- ScheduleGate compares this
    // against intervalHours on every tick to decide whether it's due.
    private Instant lastRunAt;

    public SyncSchedule( String syncType, String mode, int intervalHours )
    {
        this.syncType = syncType;
        this.mode = mode;
        this.intervalHours = intervalHours;
    }
}
