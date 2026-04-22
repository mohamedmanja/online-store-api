package com.harmony.store.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserPrincipal {
    private final String id;
    private final String email;
    private final String role;
}
