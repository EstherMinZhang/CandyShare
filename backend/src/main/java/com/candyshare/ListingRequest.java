package com.candyshare;

import javax.validation.constraints.*;

public class ListingRequest {
    @NotBlank @Size(max = 2000) public String notes;
    @NotBlank @Size(max = 100) public String region;
}
