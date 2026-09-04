package com.groww.watchlist.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;

/**
 * A point-in-time price reading for a symbol, shared across ALL users
 * (prices aren't per-user - this is the dedup layer that keeps us
 * within free-tier API rate limits no matter how many users watch AAPL).
 */
@Entity
@Table(name = "price_snapshots", indexes = @Index(columnList = "symbol,fetchedAt"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PriceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    private double price;
    private double previousClose;
    private double dayHigh;
    private double dayLow;
    private double fiftyTwoWeekHigh;
    private double fiftyTwoWeekLow;
    private long volume;

    @Column(nullable = false)
    private Instant fetchedAt;

    /** True if this snapshot came from a stale cache fallback (API call failed). */
    private boolean stale;
}
