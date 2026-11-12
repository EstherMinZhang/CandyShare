package com.candyshare;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import javax.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CandyShareIntegrationTest {
    private static final String OWNER = "owner@example.com";
    private static final String RECIPIENT = "recipient@example.com";
    private static final String OTHER = "other@example.com";
    private static final String PASSWORD = "CandySharePass123!";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate database;
    @Autowired AccountRepository accounts;
    @Autowired ListingRepository listings;
    @SpyBean ClaimRepository claims;
    @Autowired PasswordEncoder passwords;
    @Autowired SharingService sharing;
    private Account owner;

    @BeforeEach
    void prepare() {
        database.update("delete from candyshare_claims");
        database.update("delete from candyshare_listings");
        database.update("delete from candyshare_users");
        String hash = passwords.encode(PASSWORD);
        owner = accounts.saveAndFlush(new Account("Publisher", OWNER, hash, "owner-private-phone"));
        accounts.saveAndFlush(new Account("Recipient", RECIPIENT, hash, "recipient-private-phone"));
        accounts.saveAndFlush(new Account("Other", OTHER, hash, "other-private-phone"));
    }

    @Test
    void registrationLoginLogoutAndPasswordHashing() throws Exception {
        String body = "{\"name\":\"New Neighbor\",\"email\":\"NEW@example.com\","
                + "\"password\":\"" + PASSWORD + "\",\"contactDetails\":\"new-contact\"}";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        Account saved = accounts.findByEmail("new@example.com").get();
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, saved.getPasswordHash())).isTrue();
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        MockHttpSession session = login("new@example.com");
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Neighbor"));
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isOk());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).param("email", OWNER).param("password", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void realCsrfTokenAndSessionProtectWrites() throws Exception {
        mvc.perform(post("/api/auth/login").param("email", OWNER).param("password", PASSWORD))
                .andExpect(status().isForbidden());
        MvcResult tokenResult = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        HttpSession anonymous = tokenResult.getRequest().getSession(false);
        JsonNode token = json.readTree(tokenResult.getResponse().getContentAsString());
        MockHttpSession session = (MockHttpSession) anonymous;
        mvc.perform(post("/api/auth/login").session(session)
                .header(token.get("headerName").asText(), token.get("token").asText())
                .param("email", OWNER).param("password", PASSWORD)).andExpect(status().isOk());
        mvc.perform(post("/api/listings").session(session).contentType(MediaType.APPLICATION_JSON).content(listingBody()))
                .andExpect(status().isForbidden());
        MvcResult refreshed = mvc.perform(get("/api/auth/csrf").session(session)).andReturn();
        JsonNode freshToken = json.readTree(refreshed.getResponse().getContentAsString());
        mvc.perform(post("/api/listings").session(session)
                .header(freshToken.get("headerName").asText(), freshToken.get("token").asText())
                .contentType(MediaType.APPLICATION_JSON).content(listingBody())).andExpect(status().isCreated());
    }

    @Test
    void realSessionsCompleteTheWholePickupFlow() throws Exception {
        MockHttpSession ownerSession = login(OWNER);
        MockHttpSession recipientSession = login(RECIPIENT);
        JsonNode listing = json.readTree(mvc.perform(post("/api/listings").session(ownerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(listingBody()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long id = listing.get("id").asLong();
        mvc.perform(post("/api/listings/{id}/claims", id).session(recipientSession).with(csrf()))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/me/claims").session(recipientSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].listing.handoff.ownerContact").value("owner-private-phone"));
        mvc.perform(get("/api/listings/{id}", id)).andExpect(jsonPath("$.handoff").isEmpty());
        mvc.perform(post("/api/listings/{id}/complete", id).session(ownerSession).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/me/claims").session(recipientSession))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    @Test
    void anonymousAccessAndValidation() throws Exception {
        mvc.perform(get("/api/listings")).andExpect(status().isOk());
        mvc.perform(get("/api/me/listings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/claims")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/listings").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(listingBody()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/listings").with(user(OWNER)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/listings").with(user(OWNER)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\":\"   \",\"region\":\"Burnaby\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"N\",\"email\":\"invalid\",\"password\":\"short\",\"contactDetails\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicSearchDoesNotLeakPrivateDataOrTrustUserIds() throws Exception {
        long id = createListing();
        String body = mvc.perform(get("/api/listings").param("query", "CHOCOLATE").param("region", "burnaby"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("owner-private-phone", "secret-meeting-instructions", "passwordHash", OWNER);
        mvc.perform(get("/api/listings").param("region", "Vancouver"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/listings/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("$.handoff").isEmpty());
        mvc.perform(get("/api/me/listings").with(user(OWNER))).andExpect(jsonPath("$[0].ownerId").value(owner.getId()));
        mvc.perform(get("/api/me/listings").with(user(OTHER))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void reserveCancelReclaimCompleteAndHistory() throws Exception {
        long id = createListing();
        JsonNode first = reserve(id, RECIPIENT);
        assertThat(first.at("/listing/handoff/ownerContact").asText()).isEqualTo("owner-private-phone");
        mvc.perform(get("/api/listings/{id}", id).with(user(OTHER))).andExpect(jsonPath("$.handoff").isEmpty());
        mvc.perform(get("/api/listings/{id}", id).with(user(OWNER)))
                .andExpect(jsonPath("$.handoff.claimantContact").value("recipient-private-phone"));
        mvc.perform(post("/api/listings/{id}/claims", id).with(user(OTHER)).with(csrf())).andExpect(status().isConflict());
        mvc.perform(post("/api/claims/{id}/cancel", first.get("id").asLong()).with(user(RECIPIENT)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.listing.handoff").isEmpty());
        assertThat(listings.findById(id).get().getStatus()).isEqualTo(CandyListing.Status.AVAILABLE);
        mvc.perform(post("/api/claims/{id}/cancel", first.get("id").asLong()).with(user(RECIPIENT)).with(csrf()))
                .andExpect(status().isConflict());
        JsonNode next = reserve(id, OTHER);
        mvc.perform(get("/api/me/claims").with(user(RECIPIENT)))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$[0].listing.handoff").isEmpty());
        mvc.perform(post("/api/listings/{id}/complete", id).with(user(OWNER)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(get("/api/me/claims").with(user(OTHER))).andExpect(jsonPath("$[0].status").value("COMPLETED"));
        mvc.perform(post("/api/claims/{id}/cancel", next.get("id").asLong()).with(user(OTHER)).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/listings/{id}/complete", id).with(user(OWNER)).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/listings/{id}/claims", id).with(user(RECIPIENT)).with(csrf())).andExpect(status().isConflict());
        assertThat(claims.count()).isEqualTo(2);
    }

    @Test
    void ownershipAndStateRulesAreEnforcedByBackend() throws Exception {
        long id = createListing();
        mvc.perform(post("/api/listings/{id}/claims", id).with(user(OWNER)).with(csrf())).andExpect(status().isConflict());
        mvc.perform(put("/api/listings/{id}", id).with(user(OTHER)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(listingBody()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/listings/{id}/withdraw", id).with(user(OTHER)).with(csrf())).andExpect(status().isForbidden());
        JsonNode claim = reserve(id, RECIPIENT);
        mvc.perform(post("/api/claims/{id}/cancel", claim.get("id").asLong()).with(user(OWNER)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/listings/{id}/complete", id).with(user(RECIPIENT)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/listings/{id}/withdraw", id).with(user(OWNER)).with(csrf())).andExpect(status().isConflict());
        mvc.perform(put("/api/listings/{id}", id).with(user(OWNER)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(listingBody()))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/listings/{id}", id).with(user(OWNER)).with(csrf())).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void editAndWithdrawAvailableListingPreserveHistory() throws Exception {
        long id = createListing();
        mvc.perform(put("/api/listings/{id}", id).with(user(OWNER)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(listingBody().replace("Chocolate bag", "Updated bag")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.notes").value("Updated bag. Unopened chocolate."));
        mvc.perform(post("/api/listings/{id}/complete", id).with(user(OWNER)).with(csrf())).andExpect(status().isConflict());
        mvc.perform(post("/api/listings/{id}/withdraw", id).with(user(OWNER)).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/listings/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/me/listings").with(user(OWNER))).andExpect(jsonPath("$[0].status").value("WITHDRAWN"));
        assertThat(listings.existsById(id)).isTrue();
    }

    @Test
    void reservationFailureRollsBackListingStatus() throws Exception {
        long id = createListing();
        doThrow(new DataIntegrityViolationException("Simulated insert failure")).when(claims).saveAndFlush(any(Claim.class));
        mvc.perform(post("/api/listings/{id}/claims", id).with(user(RECIPIENT)).with(csrf())).andExpect(status().isConflict());
        assertThat(listings.findById(id).get().getStatus()).isEqualTo(CandyListing.Status.AVAILABLE);
        assertThat(claims.count()).isZero();
    }

    @Test
    void concurrentClaimsHaveExactlyOneWinner() throws Exception {
        long id = createListing();
        List<Integer> results = race(() -> reserveDirect(id, RECIPIENT), () -> reserveDirect(id, OTHER));
        assertThat(results).containsExactlyInAnyOrder(201, 409);
        assertThat(claims.count()).isEqualTo(1);
        assertThat(listings.findById(id).get().getStatus()).isEqualTo(CandyListing.Status.RESERVED);
    }

    @Test
    void concurrentCancellationRestoresAvailabilityOnce() throws Exception {
        long id = createListing();
        long claimId = reserve(id, RECIPIENT).get("id").asLong();
        Callable<Integer> cancel = () -> {
            try { sharing.cancel(claimId, principal(RECIPIENT)); return 200; }
            catch (ApiException exception) { return exception.getStatus().value(); }
        };
        assertThat(race(cancel, cancel)).containsExactlyInAnyOrder(200, 409);
        assertThat(listings.findById(id).get().getStatus()).isEqualTo(CandyListing.Status.AVAILABLE);
        assertThat(claims.findById(claimId).get().getStatus()).isEqualTo(Claim.Status.CANCELLED);
    }

    @Test
    void twoFieldNoteIsEnoughAndOldMetadataStaysStored() throws Exception {
        long id = json.readTree(mvc.perform(post("/api/listings").with(user(OWNER)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"Unopened gummies. Pickup Sunday.\",\"region\":\"Burnaby\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.notes").value("Unopened gummies. Pickup Sunday."))
                .andExpect(jsonPath("$.title").doesNotExist()).andExpect(jsonPath("$.approximateQuantity").doesNotExist())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        database.update("update candyshare_listings set title = ?, brand = ?, approximate_quantity = ?, handoff_instructions = ? where id = ?",
                "Older title", "Legacy brand", 30, "secret-meeting-instructions", id);
        mvc.perform(get("/api/listings/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("Older title\n\nUnopened gummies. Pickup Sunday."))
                .andExpect(jsonPath("$.handoff").isEmpty());
        mvc.perform(put("/api/listings/{id}", id).with(user(OWNER)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\":\"Older title. Still available.\",\"region\":\"Burnaby\"}"))
                .andExpect(status().isOk());
        assertThat(database.queryForObject("select brand from candyshare_listings where id = ?", String.class, id))
                .isEqualTo("Legacy brand");
        assertThat(database.queryForObject("select approximate_quantity from candyshare_listings where id = ?", Integer.class, id))
                .isEqualTo(30);
    }

    @Test
    void corsOnlyAllowsConfiguredCredentialedOrigin() throws Exception {
        mvc.perform(options("/api/listings").header("Origin", "https://candyshare.example")
                .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "X-CSRF-TOKEN"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(options("/api/listings").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
    }

    private int reserveDirect(long id, String email) {
        try { sharing.reserve(id, principal(email)); return 201; }
        catch (ApiException exception) { return exception.getStatus().value(); }
    }
    private Principal principal(String email) { return () -> email; }
    private List<Integer> race(Callable<Integer> first, Callable<Integer> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> a = executor.submit(() -> { ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new TimeoutException(); return first.call(); });
            Future<Integer> b = executor.submit(() -> { ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new TimeoutException(); return second.call(); });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return Arrays.asList(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        } finally { start.countDown(); executor.shutdownNow(); executor.awaitTermination(5, TimeUnit.SECONDS); }
    }
    private MockHttpSession login(String email) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).param("email", email).param("password", PASSWORD))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    private long createListing() throws Exception {
        return json.readTree(mvc.perform(post("/api/listings").with(user(OWNER)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(listingBody()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
    private JsonNode reserve(long id, String email) throws Exception {
        return json.readTree(mvc.perform(post("/api/listings/{id}/claims", id).with(user(email)).with(csrf()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    private String listingBody() {
        return "{\"notes\":\"Chocolate bag. Unopened chocolate.\",\"region\":\"Burnaby\",\"ownerId\":999}";
    }
}
