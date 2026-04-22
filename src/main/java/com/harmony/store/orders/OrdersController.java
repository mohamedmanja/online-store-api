package com.harmony.store.orders;

import com.harmony.store.config.UserPrincipal;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrdersController {

    private final OrdersService svc;

    @Data
    static class UpdateStatusDto {
        @NotNull private OrderStatus status;
    }

    // ── Customer ──────────────────────────────────────────────────────────────

    @GetMapping
    public List<Order> findMine(@AuthenticationPrincipal UserPrincipal principal) {
        return svc.findByUser(UUID.fromString(principal.getId()));
    }

    @GetMapping("/{id}")
    public Order findOne(@PathVariable UUID id,
                          @AuthenticationPrincipal UserPrincipal principal) {
        return svc.findOne(id, UUID.fromString(principal.getId()));
    }

    @GetMapping("/session/{sessionId}")
    public Order findBySessionId(@PathVariable String sessionId) {
        return svc.findBySessionId(sessionId);
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Order> findAll(@RequestParam(required = false) OrderStatus status) {
        return svc.findAll(status);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Order updateStatus(@PathVariable UUID id,
                               @RequestBody UpdateStatusDto dto) {
        return svc.updateStatus(id, dto.getStatus());
    }
}
