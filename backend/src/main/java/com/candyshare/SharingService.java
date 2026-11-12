package com.candyshare;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SharingService {
    private final ListingRepository listings;
    private final ClaimRepository claims;
    private final AccountService accounts;

    public SharingService(ListingRepository listings, ClaimRepository claims, AccountService accounts) {
        this.listings = listings; this.claims = claims; this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public List<ApiViews.ListingView> browse(String query, String region) {
        return listings.browse(query.trim().toLowerCase(Locale.ROOT), region.trim().toLowerCase(Locale.ROOT),
                CandyListing.Status.WITHDRAWN).stream()
                .map(listing -> new ApiViews.ListingView(listing, null, null)).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ApiViews.ListingView detail(Long id, Principal principal) {
        CandyListing listing = listing(id);
        Account viewer = principal == null ? null : accounts.current(principal);
        if (listing.getStatus() == CandyListing.Status.WITHDRAWN
                && (viewer == null || !listing.getOwner().getId().equals(viewer.getId()))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Listing was not found.");
        }
        return view(listing, viewer);
    }

    @Transactional(readOnly = true)
    public List<ApiViews.ListingView> myListings(Principal principal) {
        Account owner = accounts.current(principal);
        return listings.findByOwnerIdOrderByCreatedAtDesc(owner.getId()).stream()
                .map(listing -> view(listing, owner)).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ApiViews.ClaimView> myClaims(Principal principal) {
        Account viewer = accounts.current(principal);
        return claims.findByClaimantIdOrderByCreatedAtDesc(viewer.getId()).stream()
                .map(claim -> new ApiViews.ClaimView(claim, viewer, activeClaim(claim.getListing().getId())))
                .collect(Collectors.toList());
    }

    @Transactional
    public ApiViews.ListingView create(ListingRequest input, Principal principal) {
        Account owner = accounts.current(principal);
        return view(listings.saveAndFlush(new CandyListing(owner, input)), owner);
    }

    @Transactional
    public ApiViews.ListingView update(Long id, ListingRequest input, Principal principal) {
        Account owner = accounts.current(principal);
        CandyListing listing = locked(id);
        requireOwner(listing, owner);
        requireAvailable(listing);
        listing.update(input);
        return view(listing, owner);
    }

    @Transactional
    public ApiViews.ListingView withdraw(Long id, Principal principal) {
        Account owner = accounts.current(principal);
        CandyListing listing = locked(id);
        requireOwner(listing, owner);
        requireAvailable(listing);
        listing.setStatus(CandyListing.Status.WITHDRAWN);
        return view(listing, owner);
    }

    @Transactional
    public ApiViews.ClaimView reserve(Long id, Principal principal) {
        Account claimant = accounts.current(principal);
        CandyListing listing = locked(id);
        if (listing.getOwner().getId().equals(claimant.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "You cannot claim your own candy.");
        }
        requireAvailable(listing);
        if (listing.getBestBefore() != null && listing.getBestBefore().isBefore(LocalDate.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "This listing is past its best-before date.");
        }
        listing.setStatus(CandyListing.Status.RESERVED);
        Claim claim = claims.saveAndFlush(new Claim(listing, claimant));
        return new ApiViews.ClaimView(claim, claimant, claim);
    }

    @Transactional
    public ApiViews.ClaimView cancel(Long claimId, Principal principal) {
        Account viewer = accounts.current(principal);
        Claim initial = claim(claimId);
        CandyListing listing = locked(initial.getListing().getId());
        if (!initial.getClaimant().getId().equals(viewer.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the claimant can cancel this reservation.");
        }
        Claim active = activeClaim(listing.getId());
        if (initial.getStatus() != Claim.Status.RESERVED || active == null || !active.getId().equals(claimId)
                || listing.getStatus() != CandyListing.Status.RESERVED) {
            throw new ApiException(HttpStatus.CONFLICT, "This reservation is no longer active.");
        }
        initial.resolve(Claim.Status.CANCELLED);
        listing.setStatus(CandyListing.Status.AVAILABLE);
        return new ApiViews.ClaimView(initial, viewer, null);
    }

    @Transactional
    public ApiViews.ListingView complete(Long id, Principal principal) {
        Account owner = accounts.current(principal);
        CandyListing listing = locked(id);
        requireOwner(listing, owner);
        Claim active = activeClaim(id);
        if (listing.getStatus() != CandyListing.Status.RESERVED || active == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a reserved listing can be completed.");
        }
        active.resolve(Claim.Status.COMPLETED);
        listing.setStatus(CandyListing.Status.COMPLETED);
        return new ApiViews.ListingView(listing, owner, null);
    }

    private ApiViews.ListingView view(CandyListing listing, Account viewer) {
        return new ApiViews.ListingView(listing, viewer, activeClaim(listing.getId()));
    }
    private Claim activeClaim(Long listingId) {
        return claims.findByListingIdAndStatus(listingId, Claim.Status.RESERVED).orElse(null);
    }
    private CandyListing listing(Long id) {
        return listings.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Listing was not found."));
    }
    private CandyListing locked(Long id) {
        return listings.findLocked(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Listing was not found."));
    }
    private Claim claim(Long id) {
        return claims.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Claim was not found."));
    }
    private void requireOwner(CandyListing listing, Account viewer) {
        if (!listing.getOwner().getId().equals(viewer.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the publisher can manage this listing.");
        }
    }
    private void requireAvailable(CandyListing listing) {
        if (listing.getStatus() != CandyListing.Status.AVAILABLE) {
            throw new ApiException(HttpStatus.CONFLICT, "This candy is no longer available.");
        }
    }
}
