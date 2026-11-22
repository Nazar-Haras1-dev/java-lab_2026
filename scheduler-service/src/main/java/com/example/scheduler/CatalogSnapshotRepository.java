package com.example.scheduler;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface CatalogSnapshotRepository extends JpaRepository<CatalogSnapshot, Long> {

    boolean existsBySnapshotDate(LocalDate snapshotDate);

    List<CatalogSnapshot> findAllByOrderBySnapshotDateDesc();

    @Modifying
    @Query("delete from CatalogSnapshot s where s.snapshotDate < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDate cutoff);
}
