package com.example.system.repository;

import com.example.system.domain.Workstation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkstationRepository extends JpaRepository<Workstation, Long> {

    @Query("select distinct w from Workstation w left join fetch w.slots order by w.sortOrder asc, w.id asc")
    List<Workstation> findAllByOrderBySortOrderAscIdAsc();

    @Query("select w from Workstation w join fetch w.slots where w.id = :id")
    Optional<Workstation> findByIdWithSlots(@Param("id") Long id);
}
