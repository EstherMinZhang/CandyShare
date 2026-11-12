package com.candyshare;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimRepository extends JpaRepository<Claim, Long> {
    Optional<Claim> findByListingIdAndStatus(Long listingId, Claim.Status status);
    List<Claim> findByClaimantIdOrderByCreatedAtDesc(Long claimantId);
}
