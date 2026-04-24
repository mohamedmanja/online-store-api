package com.harmony.store.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;
import com.harmony.store.model.User;

@Entity
@Table(name = "two_factor_configs")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TwoFactorConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    @JsonIgnore
    private User user;

    @JsonIgnore
    @Column(name = "totp_secret")
    private String totpSecret;

    @Column(name = "totp_enabled", nullable = false)
    @Builder.Default
    private boolean totpEnabled = false;

    @Column(name = "email_enabled", nullable = false)
    @Builder.Default
    private boolean emailEnabled = false;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "phone_enabled", nullable = false)
    @Builder.Default
    private boolean phoneEnabled = false;

    @JsonIgnore
    @Column(name = "pending_otp_hash")
    private String pendingOtpHash;

    @JsonIgnore
    @Column(name = "pending_otp_expires")
    private Instant pendingOtpExpires;

    @Column(name = "default_method")
    private String defaultMethod;   // 'totp' | 'email' | 'phone'

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
