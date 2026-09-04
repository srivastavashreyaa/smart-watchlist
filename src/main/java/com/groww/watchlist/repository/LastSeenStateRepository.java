package com.groww.watchlist.repository;

import com.groww.watchlist.model.LastSeenState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LastSeenStateRepository extends JpaRepository<LastSeenState, Long> {
    Optional<LastSeenState> findByUserIdAndSymbol(String userId, String symbol);
}
