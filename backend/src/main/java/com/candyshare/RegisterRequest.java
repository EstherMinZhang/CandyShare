package com.candyshare;

import javax.validation.constraints.*;

public class RegisterRequest {
    @NotBlank @Size(max = 80) public String name;
    @NotBlank @Email @Size(max = 160) public String email;
    @NotBlank @Size(min = 10, max = 64) public String password;
    @NotBlank @Size(max = 300) public String contactDetails;
}
