package org.hisp.dhis.fhir.repository;

import org.hisp.dhis.fhir.domain.SyncSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncScheduleRepository extends JpaRepository<SyncSchedule, String>
{
}
