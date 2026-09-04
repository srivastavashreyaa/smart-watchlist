package com.groww.watchlist.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;

/**
 * THE core differentiator table.
 * Records the price a user last actually saw for a symbol, so on
 * their next visit we can compute "what changed since you looked" -
 * not just "what changed today" (which is what every naive watchlist shows).
 */
@Entity
@Table(name = "last_seen_state", uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "symbol"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LastSeenState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String symbol;

    private double lastSeenPrice;
    private Instant lastSeenAt;
}
