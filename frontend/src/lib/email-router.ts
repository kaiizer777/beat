/**
 * Email routing logic for NextAuth magic links.
 *
 * Routing Rules (matches backend EmailRouter):
 * - RESEND_TARGET_EMAIL (default: kaizerxdev@gmail.com) -> Resend (sandbox domain supported)
 * - All other recipients -> Brevo (production SMTP delivery)
 */

interface SendVerificationRequestParams {
  identifier: string;
  url: string;
  expires?: Date;
  provider: {
    apiKey?: string;
    from?: string;
    [key: string]: any;
  };
  theme?: {
    brandColor?: string;
    buttonText?: string;
    [key: string]: any;
  };
  request?: Request;
}

function parseSender(rawSender: string): { name: string; email: string } {
  if (!rawSender || !rawSender.trim()) {
    return { name: "Beat Digest", email: "kanekigaminz@gmail.com" };
  }
  const match = rawSender.trim().match(/^(.*?)\s*<(.+?)>$/);
  if (match) {
    return {
      name: match[1].trim() || "Beat Digest",
      email: match[2].trim() || "kanekigaminz@gmail.com",
    };
  }
  return {
    name: "Beat Digest",
    email: rawSender.trim(),
  };
}

function createMagicLinkHtml({ url, host }: { url: string; host: string }): string {
  const escapedHost = host.replace(/\./g, "&#8203;.");
  return `
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Sign in to BEAT</title>
</head>
<body style="background-color: #020617; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; margin: 0; padding: 40px 20px; color: #f8fafc;">
  <table align="center" border="0" cellpadding="0" cellspacing="0" width="100%" style="max-width: 520px; background-color: #0f172a; border-radius: 16px; border: 1px solid #1e293b; overflow: hidden; box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);">
    <tr>
      <td style="padding: 40px 32px; text-align: center;">
        <div style="display: inline-block; background: linear-gradient(135deg, #06b6d4, #2563eb); width: 48px; height: 48px; border-radius: 12px; margin-bottom: 20px; line-height: 48px; font-size: 24px; font-weight: bold; color: #ffffff;">
          📡
        </div>
        <h1 style="margin: 0 0 8px; font-size: 24px; font-weight: 800; color: #ffffff; letter-spacing: -0.025em;">
          Sign in to BEAT
        </h1>
        <p style="margin: 0 0 28px; font-size: 14px; color: #94a3b8; line-line: 1.5;">
          Click the button below to authenticate on <strong style="color: #e2e8f0;">${escapedHost}</strong>. This secure magic link expires in 24 hours.
        </p>
        <table align="center" border="0" cellpadding="0" cellspacing="0">
          <tr>
            <td align="center" style="border-radius: 10px; background: linear-gradient(135deg, #06b6d4, #2563eb);">
              <a href="${url}" target="_blank" style="display: inline-block; padding: 14px 32px; font-size: 15px; font-weight: 600; color: #ffffff; text-decoration: none; border-radius: 10px;">
                Sign In to BEAT &rarr;
              </a>
            </td>
          </tr>
        </table>
        <p style="margin: 32px 0 0; font-size: 12px; color: #64748b; line-height: 1.5;">
          If you didn't request this email, you can safely ignore it.<br>
          Trouble clicking? Paste this link into your browser:<br>
          <a href="${url}" style="color: #38bdf8; word-break: break-all; text-decoration: none;">${url}</a>
        </p>
      </td>
    </tr>
  </table>
</body>
</html>
`;
}

export async function sendVerificationRequest(params: SendVerificationRequestParams): Promise<void> {
  const { identifier: toEmail, url, provider } = params;
  const normalizedEmail = toEmail.trim().toLowerCase();
  const resendTarget = (process.env.RESEND_TARGET_EMAIL || "kaizerxdev@gmail.com").trim().toLowerCase();
  const host = new URL(url).host;

  // Development convenience: log magic link directly to terminal
  if (process.env.NODE_ENV !== "production") {
    console.log(`\n================================================================`);
    console.log(`🔗 [MAGIC LINK] Dev sign-in for: ${normalizedEmail}`);
    console.log(`👉 URL: ${url}`);
    console.log(`================================================================\n`);
  }

  const isResendTarget = normalizedEmail === resendTarget;

  if (isResendTarget) {
    const resendApiKey = process.env.AUTH_RESEND_KEY || process.env.RESEND_API_KEY || provider.apiKey;
    const from = process.env.RESEND_FROM_EMAIL || provider.from || "Beat Digest <onboarding@resend.dev>";

    if (!resendApiKey) {
      throw new Error("Resend API key is missing");
    }

    console.log(`[EmailRouter] Dispatching magic link via Resend to '${normalizedEmail}'...`);
    const res = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${resendApiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        from,
        to: normalizedEmail,
        subject: `Sign in to ${host}`,
        html: createMagicLinkHtml({ url, host }),
        text: `Sign in to ${host}\n\n${url}\n\n`,
      }),
    });

    if (!res.ok) {
      const err = await res.text();
      console.error("[EmailRouter] Resend error:", err);
      if (process.env.NODE_ENV === "production") {
        throw new Error(`Resend error: ${err}`);
      } else {
        console.warn("\n⚠️ [DEV MODE] Resend rejected delivery. Use the terminal sign-in URL above to complete login!\n");
      }
    } else {
      console.log(`[EmailRouter] Successfully dispatched magic link via Resend to '${normalizedEmail}'`);
    }
  } else {
    const brevoApiKey = process.env.BREVO_API_KEY;
    const rawFrom = process.env.BREVO_FROM_EMAIL || "Beat Digest <kanekigaminz@gmail.com>";

    if (!brevoApiKey) {
      if (process.env.NODE_ENV === "production") {
        throw new Error("Brevo API key (BREVO_API_KEY) is not configured");
      } else {
        console.warn("[EmailRouter] BREVO_API_KEY not found in dev mode, relying on dev terminal URL.");
        return;
      }
    }

    console.log(`[EmailRouter] Dispatching magic link via Brevo to '${normalizedEmail}'...`);
    const sender = parseSender(rawFrom);

    const res = await fetch("https://api.brevo.com/v3/smtp/email", {
      method: "POST",
      headers: {
        "api-key": brevoApiKey,
        "Content-Type": "application/json",
        "Accept": "application/json",
      },
      body: JSON.stringify({
        sender,
        to: [{ email: normalizedEmail }],
        subject: `Sign in to ${host}`,
        htmlContent: createMagicLinkHtml({ url, host }),
        textContent: `Sign in to ${host}\n\n${url}\n\n`,
      }),
    });

    if (!res.ok) {
      const err = await res.text();
      console.error("[EmailRouter] Brevo error:", err);
      if (process.env.NODE_ENV === "production") {
        throw new Error(`Brevo error (${res.status}): ${err}`);
      } else {
        console.warn("\n⚠️ [DEV MODE] Brevo returned an error (likely IP whitelist on local IP). Use the terminal sign-in URL above to complete login!\n");
      }
    } else {
      console.log(`[EmailRouter] Successfully dispatched magic link via Brevo to '${normalizedEmail}'`);
    }
  }
}
