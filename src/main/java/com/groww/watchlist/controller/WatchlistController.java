package com.groww.watchlist.controller;

import com.groww.watchlist.dto.AddSymbolRequest;
import com.groww.watchlist.dto.WatchlistItemView;
import com.groww.watchlist.model.WatchlistItem;
import com.groww.watchlist.service.MarketDataService;
import com.groww.watchlist.service.WatchlistService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * userId is passed as a header rather than baked into a session/auth
 * system - a deliberate scope cut for a 72-hour build. The frontend
 * generates a UUID once and stores it in localStorage. Swapping this
 * for real auth later only touches this one header, nothing else.
 */
@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;
    private final MarketDataService marketDataService;

    public WatchlistController(WatchlistService watchlistService, MarketDataService marketDataService) {
        this.watchlistService = watchlistService;
        this.marketDataService = marketDataService;
    }

    @GetMapping
    public ResponseEntity<List<WatchlistItemView>> getWatchlist(@RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(watchlistService.getWatchlistView(userId));
    }

    @PostMapping
    public ResponseEntity<WatchlistItem> addSymbol(@RequestHeader("X-User-Id") String userId,
                                                     @Valid @RequestBody AddSymbolRequest request) {
        WatchlistItem saved = watchlistService.addSymbol(userId, request.getSymbol());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/{symbol}")
    public ResponseEntity<Void> removeSymbol(@RequestHeader("X-User-Id") String userId,
                                              @PathVariable String symbol) {
        watchlistService.removeSymbol(userId, symbol);
        return ResponseEntity.noContent().build();
    }

    /**
     * Real 1-month price history for the detail panel chart. Doesn't need
     * userId - history for a symbol is the same for everyone (see
     * MarketDataService's shared-cache design rationale).
     */
    @GetMapping("/history/{symbol}")
    public ResponseEntity<List<double[]>> getHistory(@PathVariable String symbol) {
        return ResponseEntity.ok(marketDataService.getMonthHistory(symbol.toUpperCase()));
    }
}
