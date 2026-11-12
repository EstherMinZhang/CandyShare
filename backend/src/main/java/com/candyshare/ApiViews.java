package com.candyshare;

import java.time.Instant;

public final class ApiViews {
    private ApiViews() {}

    public static class UserView {
        public Long id;
        public String name;
        public String email;
        public String contactDetails;
        public UserView(Account user) {
            id = user.getId(); name = user.getName(); email = user.getEmail();
            contactDetails = user.getContactDetails();
        }
    }

    public static class HandoffView {
        public String ownerContact;
        public String claimantName;
        public String claimantContact;
        public String instructions;
        public Long claimId;
        public HandoffView(CandyListing listing, Claim claim) {
            ownerContact = listing.getOwner().getContactDetails();
            instructions = listing.getHandoffInstructions();
            if (claim != null) {
                claimId = claim.getId();
                claimantName = claim.getClaimant().getName();
                claimantContact = claim.getClaimant().getContactDetails();
            }
        }
    }

    public static class ListingView {
        public Long id;
        public Long ownerId;
        public String ownerName;
        public String notes;
        public String region;
        public CandyListing.Status status;
        public Instant createdAt;
        public HandoffView handoff;
        public ListingView(CandyListing listing, Account viewer, Claim activeClaim) {
            id = listing.getId(); ownerId = listing.getOwner().getId(); ownerName = listing.getOwner().getName();
            notes = listing.getNotes(); region = listing.getRegion();
            status = listing.getStatus(); createdAt = listing.getCreatedAt();
            boolean owner = viewer != null && ownerId.equals(viewer.getId());
            boolean claimant = viewer != null && activeClaim != null
                    && activeClaim.getClaimant().getId().equals(viewer.getId());
            if (owner || claimant) handoff = new HandoffView(listing, activeClaim);
        }
    }

    public static class ClaimView {
        public Long id;
        public Claim.Status status;
        public Instant createdAt;
        public Instant resolvedAt;
        public ListingView listing;
        public ClaimView(Claim claim, Account viewer, Claim activeClaim) {
            id = claim.getId(); status = claim.getStatus(); createdAt = claim.getCreatedAt();
            resolvedAt = claim.getResolvedAt(); listing = new ListingView(claim.getListing(), viewer, activeClaim);
        }
    }
}
