package com.groww.watchlist.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groww.watchlist.model.PriceSnapshot;
import com.groww.watchlist.repository.PriceSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Talks to Yahoo Finance's unofficial chart endpoint (no API key required).
 *
 * DESIGN DECISION: we deliberately cache aggressively (CACHE_TTL below) and
 * serve stale data with a flag rather than fail the request, because:
 *   1. Free/unofficial APIs get rate-limited or occasionally go down.
 *   2. A watchlist showing "last known price, 4 min old" is a far better
 *      user experience than a spinner or an error screen.
 * This is the "how do you handle stale/delayed data" requirement from the
 * brief, made explicit rather than accidental.
 */
@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final String YAHOO_CHART_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&range=1mo";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PriceSnapshotRepository snapshotRepository;

    public MarketDataService(PriceSnapshotRepository snapshotRepository) {
        this.snapshotRepository = snapshotRepository;
    }

    /**
     * Returns a fresh-enough snapshot for the symbol, fetching from Yahoo
     * only if our cached copy is older than CACHE_TTL. Falls back to the
     * last known snapshot (marked stale) if the live call fails.
     */
    public PriceSnapshot getQuote(String symbol) {
        Optional<PriceSnapshot> cached = snapshotRepository.findFirstBySymbolOrderByFetchedAtDesc(symbol);

        if (cached.isPresent() && isFresh(cached.get())) {
            return cached.get();
        }

        try {
            PriceSnapshot fresh = fetchFromYahoo(symbol);
            return snapshotRepository.save(fresh);
        } catch (Exception e) {
            log.warn("Live fetch failed for {}: {}. Falling back to cache.", symbol, e.getMessage());
            if (cached.isPresent()) {
                // Return a detached COPY flagged as stale - never mutate the
                // managed entity here, or JPA's dirty-checking would silently
                // persist "stale=true" onto real historical data at commit.
                return copyAsStale(cached.get());
            }
            throw new IllegalStateException("No data available for symbol " + symbol + " and live fetch failed", e);
        }
    }

    private PriceSnapshot copyAsStale(PriceSnapshot source) {
        PriceSnapshot copy = new PriceSnapshot();
        copy.setSymbol(source.getSymbol());
        copy.setPrice(source.getPrice());
        copy.setPreviousClose(source.getPreviousClose());
        copy.setDayHigh(source.getDayHigh());
        copy.setDayLow(source.getDayLow());
        copy.setFiftyTwoWeekHigh(source.getFiftyTwoWeekHigh());
        copy.setFiftyTwoWeekLow(source.getFiftyTwoWeekLow());
        copy.setVolume(source.getVolume());
        copy.setFetchedAt(source.getFetchedAt());
        copy.setStale(true);
        return copy;
    }

    private boolean isFresh(PriceSnapshot snapshot) {
        return !snapshot.isStale()
                && Duration.between(snapshot.getFetchedAt(), Instant.now()).compareTo(CACHE_TTL) < 0;
    }

    private PriceSnapshot fetchFromYahoo(String symbol) {
        HttpHeaders headers = new HttpHeaders();
        // Yahoo blocks the default Java user-agent; impersonate a browser.
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String url = String.format(YAHOO_CHART_URL, symbol);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        JsonNode root;
        try {
            root = objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("Malformed response from Yahoo for " + symbol, e);
        }

        JsonNode result = root.path("chart").path("result").get(0);
        if (result == null || result.isMissingNode()) {
            throw new IllegalStateException("No chart result for symbol " + symbol + " - is the ticker valid?");
        }
        JsonNode meta = result.path("meta");

        PriceSnapshot snapshot = new PriceSnapshot();
        snapshot.setSymbol(symbol);
        snapshot.setPrice(meta.path("regularMarketPrice").asDouble());
        snapshot.setPreviousClose(meta.path("previousClose").asDouble(meta.path("chartPreviousClose").asDouble()));
        snapshot.setDayHigh(meta.path("regularMarketDayHigh").asDouble());
        snapshot.setDayLow(meta.path("regularMarketDayLow").asDouble());
        snapshot.setFiftyTwoWeekHigh(meta.path("fiftyTwoWeekHigh").asDouble());
        snapshot.setFiftyTwoWeekLow(meta.path("fiftyTwoWeekLow").asDouble());
        snapshot.setVolume(meta.path("regularMarketVolume").asLong());
        snapshot.setFetchedAt(Instant.now());
        snapshot.setStale(false);
        return snapshot;
    }

    /** Best-effort display name lookup; falls back to the symbol itself. */
    public String resolveDisplayName(String symbol) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            String url = String.format(YAHOO_CHART_URL, symbol);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode meta = root.path("chart").path("result").get(0).path("meta");
            String name = meta.path("longName").asText(null);
            return name != null ? name : symbol;
        } catch (Exception e) {
            return symbol;
        }
    }

    /**
     * Real 1-month daily closing price history, straight from the same
     * Yahoo chart endpoint we already call for quotes - it returns this
     * for free, we just weren't reading it. Used to draw an honest price
     * chart instead of faking one from four data points.
     */
    public List<double[]> getMonthHistory(String symbol) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            String url = String.format(YAHOO_CHART_URL, symbol);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode result = root.path("chart").path("result").get(0);

            JsonNode timestamps = result.path("timestamp");
            JsonNode closes = result.path("indicators").path("quote").get(0).path("close");

            List<double[]> points = new ArrayList<>();
            for (int i = 0; i < timestamps.size(); i++) {
                JsonNode closeNode = closes.get(i);
                if (closeNode != null && !closeNode.isNull()) {
                    points.add(new double[]{timestamps.get(i).asLong(), closeNode.asDouble()});
                }
            }
            return points;
        } catch (Exception e) {
            log.warn("Could not fetch history for {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }
}
