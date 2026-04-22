package com.harmony.store.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {
    private String accessToken;
    private UserInfo user;
    private boolean requires2FA;
    private String pendingToken;
    private List<String> methods;
    private String defaultMethod;
    private String hint;

    @Data @Builder
    public static class UserInfo {
        private String id;
        private String email;
        private String name;
        private String role;
    }
}
