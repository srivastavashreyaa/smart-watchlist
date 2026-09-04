package com.groww.watchlist.service;

import com.groww.watchlist.dto.WatchlistItemView;
import com.groww.watchlist.model.LastSeenState;
import com.groww.watchlist.model.PriceSnapshot;
import com.groww.watchlist.model.WatchlistItem;
import com.groww.watchlist.repository.LastSeenStateRepository;
import com.groww.watchlist.repository.WatchlistItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class WatchlistService {

    private final WatchlistItemRepository watchlistItemRepository;
    private final LastSeenStateRepository lastSeenStateRepository;
    private final MarketDataService marketDataService;
    private final SignalService signalService;

    public WatchlistService(WatchlistItemRepository watchlistItemRepository,
                             LastSeenStateRepository lastSeenStateRepository,
                             MarketDataService marketDataService,
                             SignalService signalService) {
        this.watchlistItemRepository = watchlistItemRepository;
        this.lastSeenStateRepository = lastSeenStateRepository;
        this.marketDataService = marketDataService;
        this.signalService = signalService;
    }

    @Transactional
    public WatchlistItem addSymbol(String userId, String rawSymbol) {
        String symbol = rawSymbol.trim().toUpperCase();

        Optional<WatchlistItem> existing = watchlistItemRepository.findByUserIdAndSymbol(userId, symbol);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Validate the ticker actually resolves before we save it - fail fast
        // on typos rather than silently storing a symbol that will never load.
        String displayName = marketDataService.resolveDisplayName(symbol);
        marketDataService.getQuote(symbol); // throws if the symbol is genuinely invalid

        WatchlistItem item = new WatchlistItem(null, userId, symbol, displayName, Instant.now());
        return watchlistItemRepository.save(item);
    }

    @Transactional
    public void removeSymbol(String userId, String symbol) {
        watchlistItemRepository.deleteByUserIdAndSymbol(userId, symbol.toUpperCase());
    }

    /**
     * The main read path: for every stock in the user's list, fetch the
     * latest price, diff it against what they last saw, and decide whether
     * it deserves their attention right now. Then it updates "last seen"
     * to the current price/time, since this view IS the user checking in.
     */
    @Transactional
    public List<WatchlistItemView> getWatchlistView(String userId) {
        List<WatchlistItem> items = watchlistItemRepository.findByUserId(userId);
        List<WatchlistItemView> views = new ArrayList<>();

        for (WatchlistItem item : items) {
            PriceSnapshot quote = marketDataService.getQuote(item.getSymbol());
            Optional<LastSeenState> lastSeenOpt = lastSeenStateRepository.findByUserIdAndSymbol(userId, item.getSymbol());

            Double changeSinceLastSeenPct = null;
            boolean crossedHigh = false;
            boolean crossedLow = false;

            if (lastSeenOpt.isPresent()) {
                double lastPrice = lastSeenOpt.get().getLastSeenPrice();
                if (lastPrice > 0) {
                    changeSinceLastSeenPct = ((quote.getPrice() - lastPrice) / lastPrice) * 100.0;
                }
                crossedHigh = lastPrice < quote.getFiftyTwoWeekHigh() && quote.getPrice() >= quote.getFiftyTwoWeekHigh();
                crossedLow = lastPrice > quote.getFiftyTwoWeekLow() && quote.getPrice() <= quote.getFiftyTwoWeekLow();
            }

            double threshold = signalService.adaptiveThresholdFor(item.getSymbol());
            double effectiveChange = changeSinceLastSeenPct != null ? changeSinceLastSeenPct : 0.0;
            boolean meaningful = changeSinceLastSeenPct != null &&
                    (Math.abs(changeSinceLastSeenPct) >= threshold || crossedHigh || crossedLow);
            String reason = lastSeenOpt.isEmpty()
                    ? "First time tracking this stock"
                    : signalService.explain(effectiveChange, threshold, crossedHigh, crossedLow);

            double dayChangePct = quote.getPreviousClose() > 0
                    ? ((quote.getPrice() - quote.getPreviousClose()) / quote.getPreviousClose()) * 100.0
                    : 0.0;

            WatchlistItemView view = new WatchlistItemView();
            view.setSymbol(item.getSymbol());
            view.setDisplayName(item.getDisplayName());
            view.setCurrentPrice(quote.getPrice());
            view.setDayChangePercent(dayChangePct);
            view.setMeaningfulChange(meaningful);
            view.setReason(reason);
            view.setChangeSinceLastSeenPct(changeSinceLastSeenPct);
            view.setCrossed52WeekHigh(crossedHigh);
            view.setCrossed52WeekLow(crossedLow);
            view.setStale(quote.isStale());
            view.setFiftyTwoWeekHigh(quote.getFiftyTwoWeekHigh());
            view.setFiftyTwoWeekLow(quote.getFiftyTwoWeekLow());
            view.setDayHigh(quote.getDayHigh());
            view.setDayLow(quote.getDayLow());
            view.setThresholdPercent(threshold);
            views.add(view);

            upsertLastSeen(userId, item.getSymbol(), quote.getPrice());
        }

        assignMoverRanks(views);
        return views;
    }

    /** Marks the top 3 absolute day-movers in this specific watchlist, 1 = biggest. */
    private void assignMoverRanks(List<WatchlistItemView> views) {
        List<WatchlistItemView> sorted = new ArrayList<>(views);
        sorted.sort(Comparator.comparingDouble((WatchlistItemView v) -> Math.abs(v.getDayChangePercent())).reversed());
        for (int i = 0; i < Math.min(3, sorted.size()); i++) {
            sorted.get(i).setMoverRank(i + 1);
        }
    }

    private void upsertLastSeen(String userId, String symbol, double price) {
        LastSeenState state = lastSeenStateRepository.findByUserIdAndSymbol(userId, symbol)
                .orElse(new LastSeenState(null, userId, symbol, 0, null));
        state.setLastSeenPrice(price);
        state.setLastSeenAt(Instant.now());
        lastSeenStateRepository.save(state);
    }
}
