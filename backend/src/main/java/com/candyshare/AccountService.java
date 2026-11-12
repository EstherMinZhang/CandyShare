package com.candyshare;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;

    public AccountService(AccountRepository accounts, PasswordEncoder passwords) {
        this.accounts = accounts;
        this.passwords = passwords;
    }

    @Transactional
    public ApiViews.UserView register(RegisterRequest request) {
        String email = request.email.trim().toLowerCase(Locale.ROOT);
        if (request.password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes.");
        }
        if (accounts.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        Account account = accounts.saveAndFlush(new Account(request.name.trim(), email,
                passwords.encode(request.password), request.contactDetails.trim()));
        return new ApiViews.UserView(account);
    }

    @Transactional(readOnly = true)
    public Account current(Principal principal) {
        if (principal == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in.");
        return accounts.findByEmail(principal.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in again."));
    }
}
