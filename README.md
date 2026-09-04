# Smart Market Watchlist — Code by Groww 2026

A watchlist that doesn't just show prices — it tells you what actually
**deserves your attention** since you last checked, and why.

## The core idea

Every stock has its own normal amount of daily wobble. A 2% move on a
sleepy blue-chip is a real event; the same 2% on a small-cap is Tuesday.
Instead of one flat alert threshold for every stock, this app:

1. Tracks the price **you personally last saw** for each stock (not
   just "today's change") — so a stock that moved gradually over three
   days you didn't check in still surfaces as meaningful.
2. Computes each stock's **own recent volatility** from stored price
   history and uses that as its personal threshold, falling back to a
   sane default (1.5%) when there isn't enough history yet (cold start).
3. Also flags 52-week high/low crossings and calls out the top 3
   movers in your specific list — signals that matter regardless of
   volatility.
4. Explicitly separates **"needs your attention"** from **"everything
   else"** in the UI, rather than showing a uniform grid you have to
   scan yourself.

## Setup (tested against a clean clone)

**Requirements:** JDK 17+, Maven (or just use the IDE's built-in Maven).

```bash
# From the project root:
mvn spring-boot:run
```

Then open **http://localhost:8080** in a browser. That's it — no
database setup, no API keys. Uses an in-memory H2 database that resets
on restart (see `application.properties` for how to swap to Postgres
if you want persistence).

To add a stock, type a valid ticker (e.g. `AAPL`, `TSLA`, `TCS.NS` for
NSE-listed stocks) and hit Add. Prices come live from Yahoo Finance's
public chart endpoint — no API key required.

## Architecture

```
index.html (vanilla JS, no build step)
      │  fetch() with X-User-Id header (client-generated UUID)
      ▼
WatchlistController (REST)
      │
      ▼
WatchlistService ──────► SignalService (adaptive "meaningful change" scoring)
      │
      ▼
MarketDataService ──► Yahoo Finance chart API
      │        (60s cache, stale-fallback on failure)
      ▼
PriceSnapshotRepository / LastSeenStateRepository / WatchlistItemRepository
      │
      ▼
H2 (in-memory)

PriceHistoryScheduler: background job every 5 min, refreshes all
distinct watched symbols to keep cache warm and build volatility
history over time.
```

## Key engineering decisions (and their trade-offs)

- **No auth system.** A client-generated UUID in localStorage stands
  in for a user ID. This was a deliberate scope cut for a 72-hour
  build — swapping in real auth later only touches one header, not
  the data model.
- **Shared price cache across users, per-user "last seen" state.**
  Prices are fetched once per symbol regardless of how many users
  watch it — this is what keeps the app inside a free-tier API's rate
  limits no matter how many people use it, which matters a lot given
  Yahoo's endpoint isn't officially rate-limited but isn't guaranteed
  either.
- **Stale-serves-degraded, not error.** If the live price fetch fails,
  the last known price is served with a "cached" badge instead of an
  error screen. A slightly-old price beats a broken page.
- **H2 in-memory over Postgres.** Zero setup for judges evaluating
  quickly matters more than persistence for a hackathon demo. The
  swap is a 4-line config change, documented in
  `application.properties`.
- **Adaptive threshold, not machine learning.** A stddev-based
  threshold from stored history was chosen over anything ML-flavored
  because it's explainable in one sentence and defensible live — a
  black-box model would be a liability in the Q&A round, not an asset.

## Known limitations (and why they're acceptable for this scope)

- Volatility history resets whenever the app restarts (in-memory DB) —
  acceptable for a demo, not for production.
- Yahoo's endpoint is unofficial and could change or block requests;
  in production this would be swapped for a licensed data provider.
- No websockets/real-time push — the frontend polls every 30s, which
  is more than sufficient for a "check in occasionally" watchlist and
  avoids the complexity of a persistent connection for this scope.

## 100-word product pitch (draft — edit in your own words before submitting)

Most watchlists show you a price. This one decides whether that price
actually deserves your attention. Each stock gets its own volatility
baseline, computed from its own recent history — a 2% move means
something different for a blue-chip than a small-cap, so a flat alert
threshold is the wrong model. The app tracks what you personally last
saw (not just "today's change"), flags 52-week high/low crossings, and
visually separates signal from noise instead of a uniform grid. Prices
are cached and shared across users to respect free-tier API limits,
with graceful stale-data fallback rather than errors.
