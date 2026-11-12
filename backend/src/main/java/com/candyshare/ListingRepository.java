package com.candyshare;

import java.util.List;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ListingRepository extends JpaRepository<CandyListing, Long> {
    List<CandyListing> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    @Query("select l from CandyListing l where l.status <> :withdrawn "
            + "and (:query = '' or lower(l.title) like concat('%', :query, '%') "
            + "or lower(l.description) like concat('%', :query, '%')) "
            + "and (:region = '' or lower(l.region) = :region) order by l.createdAt desc")
    List<CandyListing> browse(@Param("query") String query, @Param("region") String region,
            @Param("withdrawn") CandyListing.Status withdrawn);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from CandyListing l where l.id = :id")
    Optional<CandyListing> findLocked(@Param("id") Long id);
}
