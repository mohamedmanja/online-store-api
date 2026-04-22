package com.harmony.store.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;
import com.harmony.store.model.User;

@Entity
@Table(name = "user_preferences")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class UserPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @Column(name = "default_shipping_address_id")
    private UUID defaultShippingAddressId;

    @Column(name = "default_billing_address_id")
    private UUID defaultBillingAddressId;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
