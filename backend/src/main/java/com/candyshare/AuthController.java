package com.candyshare;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accounts;
    public AuthController(AccountService accounts) { this.accounts = accounts; }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("headerName", token.getHeaderName());
        response.put("token", token.getToken());
        return response;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiViews.UserView register(@Valid @RequestBody RegisterRequest request) {
        return accounts.register(request);
    }

    @GetMapping("/me")
    public ApiViews.UserView me(Principal principal) {
        return new ApiViews.UserView(accounts.current(principal));
    }
}
