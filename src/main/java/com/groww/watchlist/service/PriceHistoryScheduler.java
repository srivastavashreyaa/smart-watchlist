package com.groww.watchlist.service;

import com.groww.watchlist.repository.WatchlistItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Periodically refreshes prices for every DISTINCT symbol currently
 * watched by anyone (not per-user - see MarketDataService docs), which
 * both keeps the cache warm for fast reads and builds up the price
 * history that SignalService needs for adaptive thresholds.
 *
 * Runs every 5 minutes: frequent enough to build meaningful history
 * within a 72-hour demo window, sparse enough to stay well inside any
 * free-tier rate limit even with dozens of distinct symbols.
 */
@Component
public class PriceHistoryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PriceHistoryScheduler.class);

    private final WatchlistItemRepository watchlistItemRepository;
    private final MarketDataService marketDataService;

    public PriceHistoryScheduler(WatchlistItemRepository watchlistItemRepository,
                                  MarketDataService marketDataService) {
        this.watchlistItemRepository = watchlistItemRepository;
        this.marketDataService = marketDataService;
    }

    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void refreshAllWatchedSymbols() {
        Set<String> symbols = watchlistItemRepository.findAll().stream()
                .map(item -> item.getSymbol())
                .collect(Collectors.toSet());

        for (String symbol : symbols) {
            try {
                marketDataService.getQuote(symbol);
            } catch (Exception e) {
                log.warn("Background refresh failed for {}: {}", symbol, e.getMessage());
            }
        }
    }
}
