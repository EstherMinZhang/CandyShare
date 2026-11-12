package com.candyshare;

import java.time.Instant;
import java.time.LocalDate;
import javax.persistence.*;

@Entity
@Table(name = "candyshare_listings")
public class CandyListing {
    public enum Status { AVAILABLE, RESERVED, COMPLETED, WITHDRAWN }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private Account owner;
    // Legacy columns remain mapped so existing databases and stored metadata are preserved.
    @Column(nullable = false, length = 120)
    private String title = "";
    @Column(nullable = false, length = 2000)
    private String description;
    @Column(length = 100)
    private String brand;
    @Column(length = 1000)
    private String imageUrl;
    @Column(nullable = false)
    private Integer approximateQuantity = 0;
    @Column(nullable = false)
    private boolean unopenedConfirmed;
    @Column(nullable = false, length = 500)
    private String allergenInfo = "Unknown";
    private LocalDate bestBefore;
    @Column(nullable = false, length = 100)
    private String region;
    @Column(nullable = false, length = 500)
    private String pickupTime = "Arrange pickup after reserving.";
    @Column(nullable = false, length = 1000)
    private String handoffInstructions = "";
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status = Status.AVAILABLE;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected CandyListing() {}
    public CandyListing(Account owner, ListingRequest input) {
        this.owner = owner;
        update(input);
    }
    public void update(ListingRequest input) {
        title = "";
        description = input.notes.trim();
        region = input.region.trim();
    }
    public Long getId() { return id; }
    public Account getOwner() { return owner; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getNotes() {
        return title == null || title.isEmpty() ? description : title + "\n\n" + description;
    }
    public String getBrand() { return brand; }
    public String getImageUrl() { return imageUrl; }
    public Integer getApproximateQuantity() { return approximateQuantity; }
    public boolean isUnopenedConfirmed() { return unopenedConfirmed; }
    public String getAllergenInfo() { return allergenInfo; }
    public LocalDate getBestBefore() { return bestBefore; }
    public String getRegion() { return region; }
    public String getPickupTime() { return pickupTime; }
    public String getHandoffInstructions() { return handoffInstructions; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
