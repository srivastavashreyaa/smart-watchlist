package com.groww.watchlist.repository;

import com.groww.watchlist.model.PriceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long> {

    Optional<PriceSnapshot> findFirstBySymbolOrderByFetchedAtDesc(String symbol);

    @Query("SELECT p FROM PriceSnapshot p WHERE p.symbol = :symbol AND p.fetchedAt >= :since ORDER BY p.fetchedAt ASC")
    List<PriceSnapshot> findRecentHistory(@Param("symbol") String symbol, @Param("since") Instant since);
}
