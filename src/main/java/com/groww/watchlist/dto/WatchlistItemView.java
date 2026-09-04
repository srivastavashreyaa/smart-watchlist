package com.groww.watchlist.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * What the frontend actually renders per row. This is where the whole
 * product idea becomes visible: not just a price, but "did this deserve
 * your attention, and why".
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistItemView {
    private String symbol;
    private String displayName;
    private double currentPrice;
    private double dayChangePercent;

    // The core differentiator fields
    private boolean meaningfulChange;
    private String reason;                 // human-readable: "Up 4.2% - beyond normal range"
    private Double changeSinceLastSeenPct;  // null if first time viewing this stock
    private boolean crossed52WeekHigh;
    private boolean crossed52WeekLow;
    private Integer moverRank;              // 1 = biggest mover in this watchlist today, null if not top 3

    private boolean stale;                  // true if served from cache due to API failure

    // Exposed for the frontend's 52-week range visual - already computed
    // server-side for the crossed52WeekHigh/Low flags, just wasn't surfaced before.
    private double fiftyTwoWeekHigh;
    private double fiftyTwoWeekLow;
    private double dayHigh;
    private double dayLow;
    private double thresholdPercent; // the adaptive threshold used to judge this specific stock
}
