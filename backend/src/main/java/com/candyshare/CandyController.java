package com.candyshare;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Validated
public class CandyController {
    private final SharingService sharing;
    public CandyController(SharingService sharing) { this.sharing = sharing; }

    @GetMapping("/listings")
    public List<ApiViews.ListingView> browse(@RequestParam(defaultValue = "") @Size(max = 120) String query,
            @RequestParam(defaultValue = "") @Size(max = 100) String region) {
        return sharing.browse(query, region);
    }
    @GetMapping("/listings/{id}")
    public ApiViews.ListingView detail(@PathVariable Long id, Principal principal) { return sharing.detail(id, principal); }
    @GetMapping("/me/listings")
    public List<ApiViews.ListingView> myListings(Principal principal) { return sharing.myListings(principal); }
    @GetMapping("/me/claims")
    public List<ApiViews.ClaimView> myClaims(Principal principal) { return sharing.myClaims(principal); }
    @PostMapping("/listings")
    public ResponseEntity<ApiViews.ListingView> create(@Valid @RequestBody ListingRequest input, Principal principal) {
        ApiViews.ListingView result = sharing.create(input, principal);
        return ResponseEntity.created(URI.create("/api/listings/" + result.id)).body(result);
    }
    @PutMapping("/listings/{id}")
    public ApiViews.ListingView update(@PathVariable Long id, @Valid @RequestBody ListingRequest input, Principal principal) {
        return sharing.update(id, input, principal);
    }
    @PostMapping("/listings/{id}/withdraw")
    public ApiViews.ListingView withdraw(@PathVariable Long id, Principal principal) { return sharing.withdraw(id, principal); }
    @PostMapping("/listings/{id}/claims")
    public ResponseEntity<ApiViews.ClaimView> reserve(@PathVariable Long id, Principal principal) {
        ApiViews.ClaimView result = sharing.reserve(id, principal);
        return ResponseEntity.created(URI.create("/api/me/claims")).body(result);
    }
    @PostMapping("/claims/{id}/cancel")
    public ApiViews.ClaimView cancel(@PathVariable Long id, Principal principal) { return sharing.cancel(id, principal); }
    @PostMapping("/listings/{id}/complete")
    public ApiViews.ListingView complete(@PathVariable Long id, Principal principal) { return sharing.complete(id, principal); }
}
