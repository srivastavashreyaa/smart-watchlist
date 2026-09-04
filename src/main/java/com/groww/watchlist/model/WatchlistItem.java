package com.groww.watchlist.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;

/**
 * A single stock a user is tracking.
 * userId is a client-generated UUID (stored in browser localStorage) -
 * deliberately avoiding full auth so the hackathon build stays scoped,
 * while still supporting true multi-user, multi-device separation.
 */
@Entity
@Table(name = "watchlist_items", uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "symbol"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String symbol;

    private String displayName;

    @Column(nullable = false)
    private Instant addedAt;
}
