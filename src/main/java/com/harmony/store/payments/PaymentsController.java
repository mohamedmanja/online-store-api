package com.harmony.store.payments;

import com.harmony.store.config.UserPrincipal;
import com.harmony.store.payments.dto.CreateCheckoutDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentsController {

    private final PaymentsService svc;

    @PostMapping("/checkout")
    public Map<String, String> createCheckout(
            @Valid @RequestBody CreateCheckoutDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return svc.createCheckoutSession(dto, principal.getId());
    }

    @PostMapping("/webhook")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Boolean> webhook(HttpServletRequest request,
                                         @RequestHeader("stripe-signature") String signature) throws IOException {
        byte[] rawBody = request.getInputStream().readAllBytes();
        svc.handleWebhook(rawBody, signature);
        return Map.of("received", true);
    }
}
