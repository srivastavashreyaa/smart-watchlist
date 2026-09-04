package com.groww.watchlist.repository;

import com.groww.watchlist.model.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {
    List<WatchlistItem> findByUserId(String userId);
    Optional<WatchlistItem> findByUserIdAndSymbol(String userId, String symbol);
    void deleteByUserIdAndSymbol(String userId, String symbol);
}
