package com.nitramite.porssiohjain.services;

import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final MessageSource messageSource;

    @Value("${resent.api-key}")
    private String resentApiKey;

    @Value("${app.alerts.mail}")
    private String from;

    public void sendPowerLimitExceededEmail(
            String recipientEmail,
            String powerLimitName,
            BigDecimal limitKw,
            BigDecimal currentAvgKw,
            Locale locale
    ) {
        try {
            String subject = messageSource.getMessage(
                    "mail.powerLimitExceeded.subject",
                    new Object[]{powerLimitName},
                    locale
            );

            String title = messageSource.getMessage(
                    "mail.powerLimitExceeded.title",
                    null,
                    locale
            );

            String intro = messageSource.getMessage(
                    "mail.powerLimitExceeded.intro",
                    new Object[]{powerLimitName},
                    locale
            );

            String limitLabel = messageSource.getMessage(
                    "mail.powerLimitExceeded.limit",
                    null,
                    locale
            );

            String currentAvgLabel = messageSource.getMessage(
                    "mail.powerLimitExceeded.currentAvg",
                    null,
                    locale
            );

            String footer = messageSource.getMessage(
                    "mail.powerLimitExceeded.footer",
                    null,
                    locale
            );

            String htmlBody = """
                    <div style="font-family: Arial, sans-serif; color: #333;">
                        <h2 style="color: #d32f2f;">%s</h2>
                    
                        <p>%s</p>
                    
                        <table style="border-collapse: collapse; margin-top: 12px;">
                            <tr>
                                <td style="padding: 6px 12px; font-weight: bold;">%s:</td>
                                <td style="padding: 6px 12px;">%s kW</td>
                            </tr>
                            <tr>
                                <td style="padding: 6px 12px; font-weight: bold;">%s:</td>
                                <td style="padding: 6px 12px;">%s kW</td>
                            </tr>
                        </table>
                    
                        <hr style="margin-top: 24px;" />
                        <p style="font-size: 12px; color: #777;">%s</p>
                    </div>
                    """
                    .formatted(
                            title,
                            intro,
                            limitLabel,
                            limitKw.stripTrailingZeros().toPlainString(),
                            currentAvgLabel,
                            currentAvgKw.stripTrailingZeros().toPlainString(),
                            footer
                    );

            Resend resend = new Resend(resentApiKey);
            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from(from)
                    .to(recipientEmail)
                    .subject(subject)
                    .html(htmlBody)
                    .build();
            CreateEmailResponse data = resend.emails().send(params);
            log.info("Power limit exceeded email notification sent with Resend id {}", data.getId());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send power limit email", e);
        }
    }
}