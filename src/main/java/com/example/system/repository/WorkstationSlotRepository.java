package com.example.system.repository;

import com.example.system.domain.WorkstationSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkstationSlotRepository extends JpaRepository<WorkstationSlot, Long> {

    long countByAudioPath(String audioPath);

    @Query(
            "select s from WorkstationSlot s join fetch s.workstation w where w.enabled = true order by w.sortOrder asc, w.id asc, s.role asc")
    List<WorkstationSlot> findSlotsForOrchestration();

    @Query(
            "select s from WorkstationSlot s join fetch s.workstation w where s.inputBitIndex = :bit and (:excludeId is null or s.id <> :excludeId)")
    Optional<WorkstationSlot> findConflictingSlotByInputBit(
            @Param("bit") int bit, @Param("excludeId") Long excludeSlotId);

    @Query(
            "select s from WorkstationSlot s join fetch s.workstation w where s.outputSlaveId = :slave and s.outputChannel = :relay and (:excludeId is null or s.id <> :excludeId)")
    Optional<WorkstationSlot> findConflictingSlotByOutput(
            @Param("slave") int slave, @Param("relay") int relay, @Param("excludeId") Long excludeSlotId);
}
