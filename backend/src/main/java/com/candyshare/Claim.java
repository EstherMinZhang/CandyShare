package com.candyshare;

import java.time.Instant;
import javax.persistence.*;

@Entity
@Table(name = "candyshare_claims")
public class Claim {
    public enum Status { RESERVED, CANCELLED, COMPLETED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private CandyListing listing;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claimant_id", nullable = false)
    private Account claimant;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status = Status.RESERVED;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    private Instant resolvedAt;

    protected Claim() {}
    public Claim(CandyListing listing, Account claimant) {
        this.listing = listing;
        this.claimant = claimant;
    }
    public Long getId() { return id; }
    public CandyListing getListing() { return listing; }
    public Account getClaimant() { return claimant; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void resolve(Status status) { this.status = status; resolvedAt = Instant.now(); }
}
