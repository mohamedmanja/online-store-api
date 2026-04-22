package com.harmony.store.service;

import com.harmony.store.model.Order;
import com.harmony.store.model.OrderItem;
import com.harmony.store.model.Shipment;
import com.harmony.store.model.User;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import com.harmony.store.model.Product;

@Slf4j
@Service
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.from:no-reply@harmony.store}")
    private String from;

    @Value("${app.minio.url:http://localhost:9000/mmanja-com-online-store}")
    private String minioBase;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // ── Two-factor OTP ────────────────────────────────────────────────────────

    public void sendTwoFactorOtp(User user, String otp) {
        String displayName = user.getName() != null ? user.getName() : user.getEmail();
        String html = """
                <!DOCTYPE html><html lang="en">
                <head><meta charset="UTF-8"/></head>
                <body style="margin:0;padding:0;background:#0a0b0d;font-family:'Helvetica Neue',Helvetica,Arial,sans-serif;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background:#0a0b0d;padding:40px 16px;">
                    <tr><td align="center">
                      <table role="presentation" style="max-width:480px;background:#17181b;border-radius:8px;border:1px solid #2a2b2e;overflow:hidden;">
                        <tr><td style="background:#0f1012;padding:28px 40px;border-bottom:1px solid #2a2b2e;">
                          <p style="margin:0;font-size:11px;letter-spacing:.18em;text-transform:uppercase;color:rgba(232,228,220,0.35);">Harmony</p>
                          <p style="margin:6px 0 0;font-size:20px;color:#e8e4dc;">Verification code</p>
                        </td></tr>
                        <tr><td style="padding:32px 40px;">
                          <p style="margin:0 0 24px;font-size:14px;color:rgba(232,228,220,0.6);line-height:1.7;">
                            Hi %s,<br/>Use the code below to complete your sign-in. It expires in 10 minutes.
                          </p>
                          <div style="background:#0f1012;border:1px solid #2a2b2e;border-radius:6px;padding:24px;text-align:center;">
                            <p style="margin:0;font-size:36px;font-weight:700;letter-spacing:8px;color:#e8e4dc;font-family:monospace;">%s</p>
                          </div>
                          <p style="margin:20px 0 0;font-size:12px;color:rgba(232,228,220,0.25);">If you didn't try to sign in, you can safely ignore this email.</p>
                        </td></tr>
                        <tr><td style="padding:20px 40px;border-top:1px solid #2a2b2e;">
                          <p style="margin:0;font-size:11px;color:rgba(232,228,220,0.2);text-align:center;">© 2025 Harmony. All rights reserved.</p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(displayName, otp);

        send(user.getEmail(), "Your Harmony login code: " + otp, html);
    }

    // ── Password reset ────────────────────────────────────────────────────────

    public void sendPasswordReset(User user, String rawToken) {
        String resetUrl    = frontendUrl + "/reset-password?token=" + rawToken;
        String displayName = user.getName() != null ? user.getName() : user.getEmail();
        String html = """
                <!DOCTYPE html><html lang="en">
                <head><meta charset="UTF-8"/></head>
                <body style="margin:0;padding:0;background:#0a0b0d;font-family:'Helvetica Neue',Helvetica,Arial,sans-serif;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background:#0a0b0d;padding:40px 16px;">
                    <tr><td align="center">
                      <table role="presentation" style="max-width:480px;background:#17181b;border-radius:8px;border:1px solid #2a2b2e;overflow:hidden;">
                        <tr><td style="background:#0f1012;padding:28px 40px;border-bottom:1px solid #2a2b2e;">
                          <p style="margin:0;font-size:11px;letter-spacing:.18em;text-transform:uppercase;color:rgba(232,228,220,0.35);">Harmony</p>
                          <p style="margin:6px 0 0;font-size:20px;color:#e8e4dc;">Reset your password</p>
                        </td></tr>
                        <tr><td style="padding:32px 40px;">
                          <p style="margin:0 0 24px;font-size:14px;color:rgba(232,228,220,0.6);line-height:1.7;">
                            Hi %s,<br/>We received a request to reset your password. This link expires in 10 minutes.
                          </p>
                          <div style="text-align:center;margin-bottom:24px;">
                            <a href="%s" style="display:inline-block;background:#e8e4dc;color:#0f1012;text-decoration:none;padding:13px 32px;border-radius:4px;font-size:14px;font-weight:500;">
                              Reset password
                            </a>
                          </div>
                          <p style="margin:0 0 8px;font-size:12px;color:rgba(232,228,220,0.25);">Or copy this link:</p>
                          <p style="margin:0;font-size:11px;color:rgba(232,228,220,0.35);word-break:break-all;">%s</p>
                        </td></tr>
                        <tr><td style="padding:20px 40px;border-top:1px solid #2a2b2e;">
                          <p style="margin:0;font-size:11px;color:rgba(232,228,220,0.2);text-align:center;">© 2025 Harmony. All rights reserved.</p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(displayName, resetUrl, resetUrl);

        send(user.getEmail(), "Reset your Harmony password", html);
    }

    // ── Order confirmation ────────────────────────────────────────────────────

    public void sendOrderConfirmation(Order order, User user) {
        String html = buildOrderEmail(order, user);
        String orderNum = order.getId().toString().substring(0, 8).toUpperCase();
        send(user.getEmail(), "Order confirmed — #" + orderNum, html);
    }

    // ── Shipping notifications ────────────────────────────────────────────────

    public void sendShippingNotification(Order order, User user, Shipment shipment, String event) {
        String orderNum  = order.getId().toString().substring(0, 8).toUpperCase();
        String carrier   = shipment.getCarrier().toUpperCase();
        String tracking  = shipment.getTrackingNumber() != null ? shipment.getTrackingNumber() : "N/A";
        String service   = shipment.getServiceName();

        String subject  = switch (event) {
            case "shipped"          -> "Your order #" + orderNum + " has shipped";
            case "out_for_delivery" -> "Your order #" + orderNum + " is out for delivery";
            case "delivered"        -> "Your order #" + orderNum + " has been delivered";
            default -> "Shipping update for order #" + orderNum;
        };
        String headline = switch (event) {
            case "shipped"          -> "Your order is on its way";
            case "out_for_delivery" -> "Out for delivery today";
            case "delivered"        -> "Your order has arrived";
            default -> "Shipping update";
        };
        String body = switch (event) {
            case "shipped"          -> "Great news — your order has been picked up by " + carrier + " and is on its way to you.";
            case "out_for_delivery" -> "Your package is out for delivery and should arrive today.";
            case "delivered"        -> "Your order has been delivered. We hope you enjoy your purchase!";
            default -> "Your shipment status has been updated.";
        };

        String displayName = user.getName() != null ? user.getName() : user.getEmail();
        String trackingUrl = shipment.getTrackingUrl();
        String trackBtn = trackingUrl != null
                ? """
                  <div style="text-align:center;margin-top:28px;">
                    <a href="%s" style="display:inline-block;background:#e8e4dc;color:#0f1012;text-decoration:none;padding:12px 28px;border-radius:3px;font-size:13px;font-weight:500;">
                      Track your package
                    </a>
                  </div>
                  """.formatted(trackingUrl)
                : "";

        String estLine = "";
        if (shipment.getEstimatedDelivery() != null && "shipped".equals(event)) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)
                    .withZone(ZoneId.of("UTC"));
            estLine = """
                    <p style="margin:12px 0 0;font-size:13px;color:rgba(232,228,220,0.5);">
                      Estimated delivery: <strong style="color:#e8e4dc;">%s</strong>
                    </p>
                    """.formatted(fmt.format(shipment.getEstimatedDelivery()));
        }

        String html = """
                <!DOCTYPE html><html lang="en">
                <head><meta charset="UTF-8"/></head>
                <body style="margin:0;padding:0;background:#0a0b0d;font-family:'Helvetica Neue',Helvetica,Arial,sans-serif;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background:#0a0b0d;padding:40px 16px;">
                    <tr><td align="center">
                      <table role="presentation" style="max-width:520px;background:#17181b;border-radius:8px;border:1px solid #2a2b2e;overflow:hidden;">
                        <tr><td style="background:#0f1012;padding:28px 40px;border-bottom:1px solid #2a2b2e;">
                          <p style="margin:0;font-size:11px;letter-spacing:.18em;text-transform:uppercase;color:rgba(232,228,220,0.35);">Harmony</p>
                          <p style="margin:6px 0 0;font-size:20px;color:#e8e4dc;">%s</p>
                        </td></tr>
                        <tr><td style="padding:32px 40px;">
                          <p style="margin:0 0 20px;font-size:14px;color:rgba(232,228,220,0.6);line-height:1.7;">Hi %s,<br/>%s</p>
                          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0"
                            style="background:#0f1012;border:1px solid #2a2b2e;border-radius:6px;">
                            <tr>
                              <td style="padding:16px 20px;border-right:1px solid #2a2b2e;">
                                <p style="margin:0 0 4px;font-size:10px;letter-spacing:.1em;text-transform:uppercase;color:rgba(232,228,220,0.3);">Order</p>
                                <p style="margin:0;font-size:14px;font-weight:500;color:#e8e4dc;font-family:monospace;">#%s</p>
                              </td>
                              <td style="padding:16px 20px;border-right:1px solid #2a2b2e;">
                                <p style="margin:0 0 4px;font-size:10px;letter-spacing:.1em;text-transform:uppercase;color:rgba(232,228,220,0.3);">Carrier</p>
                                <p style="margin:0;font-size:14px;color:#e8e4dc;">%s · %s</p>
                              </td>
                              <td style="padding:16px 20px;">
                                <p style="margin:0 0 4px;font-size:10px;letter-spacing:.1em;text-transform:uppercase;color:rgba(232,228,220,0.3);">Tracking</p>
                                <p style="margin:0;font-size:13px;font-family:monospace;color:#e8e4dc;">%s</p>
                              </td>
                            </tr>
                          </table>
                          %s%s
                        </td></tr>
                        <tr><td style="padding:20px 40px;border-top:1px solid #2a2b2e;">
                          <p style="margin:0;font-size:11px;color:rgba(232,228,220,0.2);text-align:center;">© 2025 Harmony. All rights reserved.</p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(headline, displayName, body, orderNum, carrier, service, tracking, estLine, trackBtn);

        send(user.getEmail(), subject, html);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void send(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom("\"Harmony\" <" + from + ">");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email sent to {} — {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    private String buildOrderEmail(Order order, User user) {
        String orderNum  = order.getId().toString().substring(0, 8).toUpperCase();
        NumberFormat money = NumberFormat.getCurrencyInstance(Locale.US);
        String total = money.format(order.getTotal());
        String displayName = user.getName() != null ? user.getName() : user.getEmail();

        StringBuilder itemsHtml = new StringBuilder();
        List<OrderItem> items = order.getItems();
        if (items != null) {
            for (OrderItem item : items) {
                String name      = item.getProduct() != null ? item.getProduct().getName() : "Product";
                String price     = money.format(item.getUnitPrice());
                String lineTotal = money.format(item.getUnitPrice().multiply(java.math.BigDecimal.valueOf(item.getQuantity())));
                String imgUrl    = item.getProduct() != null && item.getProduct().getImageUrl() != null
                        ? minioBase + item.getProduct().getImageUrl() : null;

                String imgCell = imgUrl != null
                        ? """
                          <img src="%s" alt="%s" width="72" height="72"
                            style="width:72px;height:72px;object-fit:cover;border-radius:4px;display:block;" />
                          """.formatted(imgUrl, name)
                        : "<div style=\"width:72px;height:72px;border-radius:4px;background:#2a2b2e;\"></div>";

                itemsHtml.append("""
                        <tr>
                          <td style="padding:16px 0;border-bottom:1px solid #2a2b2e;vertical-align:top;width:88px;">%s</td>
                          <td style="padding:16px 12px;border-bottom:1px solid #2a2b2e;vertical-align:top;">
                            <p style="margin:0 0 4px;font-size:15px;font-weight:500;color:#e8e4dc;">%s</p>
                            <p style="margin:0;font-size:13px;color:rgba(232,228,220,0.45);">Qty: %d &nbsp;×&nbsp; %s</p>
                          </td>
                          <td style="padding:16px 0;border-bottom:1px solid #2a2b2e;vertical-align:top;text-align:right;">
                            <p style="margin:0;font-size:15px;font-weight:500;color:#e8e4dc;">%s</p>
                          </td>
                        </tr>
                        """.formatted(imgCell, name, item.getQuantity(), price, lineTotal));
            }
        }

        return """
                <!DOCTYPE html><html lang="en">
                <head><meta charset="UTF-8"/></head>
                <body style="margin:0;padding:0;background:#0a0b0d;font-family:'Helvetica Neue',Helvetica,Arial,sans-serif;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background:#0a0b0d;padding:40px 16px;">
                    <tr><td align="center">
                      <table role="presentation" style="max-width:580px;background:#17181b;border-radius:8px;border:1px solid #2a2b2e;overflow:hidden;">
                        <tr><td style="background:#0f1012;padding:32px 40px;border-bottom:1px solid #2a2b2e;">
                          <p style="margin:0;font-size:11px;letter-spacing:.18em;text-transform:uppercase;color:rgba(232,228,220,0.35);">Harmony</p>
                          <p style="margin:6px 0 0;font-size:22px;color:#e8e4dc;">Order confirmed</p>
                        </td></tr>
                        <tr><td style="padding:28px 40px 0;">
                          <p style="margin:0;font-size:14px;color:rgba(232,228,220,0.65);line-height:1.7;">
                            Hi %s,<br/>Thank you for your order. We've received your payment and will get your items on their way soon.
                          </p>
                        </td></tr>
                        <tr><td style="padding:24px 40px;">
                          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0"
                            style="background:#0f1012;border-radius:6px;border:1px solid #2a2b2e;">
                            <tr>
                              <td style="padding:16px 20px;border-right:1px solid #2a2b2e;">
                                <p style="margin:0 0 4px;font-size:10px;letter-spacing:.12em;text-transform:uppercase;color:rgba(232,228,220,0.32);">Order</p>
                                <p style="margin:0;font-size:14px;font-weight:500;color:#e8e4dc;font-family:monospace;">#%s</p>
                              </td>
                              <td style="padding:16px 20px;">
                                <p style="margin:0 0 4px;font-size:10px;letter-spacing:.12em;text-transform:uppercase;color:rgba(232,228,220,0.32);">Total</p>
                                <p style="margin:0;font-size:14px;font-weight:500;color:#e8e4dc;">%s</p>
                              </td>
                            </tr>
                          </table>
                        </td></tr>
                        <tr><td style="padding:0 40px;">
                          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
                            %s
                            <tr>
                              <td colspan="2" style="padding:16px 0 0;text-align:right;">
                                <p style="margin:0;font-size:13px;color:rgba(232,228,220,0.45);">Order total</p>
                              </td>
                              <td style="padding:16px 0 0;text-align:right;">
                                <p style="margin:0;font-size:18px;font-weight:500;color:#e8e4dc;">%s</p>
                              </td>
                            </tr>
                          </table>
                        </td></tr>
                        <tr><td style="padding:24px 40px 32px;border-top:1px solid #2a2b2e;margin-top:28px;">
                          <p style="margin:0;font-size:12px;color:rgba(232,228,220,0.25);line-height:1.7;text-align:center;">
                            © 2025 Harmony. All rights reserved.
                          </p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(displayName, orderNum, total, itemsHtml, total);
    }
}
