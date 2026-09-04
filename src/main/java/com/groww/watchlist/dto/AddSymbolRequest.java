package com.groww.watchlist.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddSymbolRequest {
    @NotBlank(message = "symbol must not be blank")
    private String symbol;
}
