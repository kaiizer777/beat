# Beat — Personalized Multi-Cron News Research Digest

Beat is a self-hosted, personalized multi-channel news research digest. Define distinct topics with custom schedule times and article target counts (e.g. "AI/ML — 20 articles — 8:00 AM daily", "AI Agents — 10 articles — 1:00 PM daily"), and receive clean, AI-summarized digests by email and viewable in the web UI.

---

## 🛠️ Stack & Architecture

| Layer | Technology | Cost Tier |
|---|---|---|
| **Backend** | Java 21, Spring Boot 3.4 (Spring Web, Spring Data JPA, Spring Scheduling) | Free |
| **Frontend** | Next.js (App Router), TypeScript, Tailwind CSS | Free |
| **Database** | PostgreSQL (Neon serverless free tier) | Free |
| **Research API** | TinyFish (`domain_type=news` search + markdown fetch) + Jina AI Reader (`r.jina.ai`) fallback | Free |
| **LLM Engine** | Groq API (`llama-3.3-70b-versatile`) — exactly 2 calls per channel run | Free (1,000 RPD) |
| **Email Delivery** | Resend API (Primary) / Brevo API (Fallback) | Free |
| **Hosting** | Oracle Cloud Always Free VM (Backend `systemd`) + Vercel (Frontend) | Free |

---

## ⚡ Pipeline Flow

```
Channel Cron Schedule Fired / Manual "Run Now" Triggered
  │
  ├── 1. Query Expansion (3-5 targeted sub-queries)
  ├── 2. TinyFish News Search (Multi-query candidate collection)
  ├── 3. Title Normalization & Deduplication Pass 1
  ├── 4. Content Fetching (TinyFish Fetch + Jina AI Reader fallback)
  ├── 5. Groq LLM Call 1: Cluster & Rank (trims candidates to target count)
  ├── 6. Groq LLM Call 2: Synthesize "Why It Matters" blurbs (grounded text)
  ├── 7. Persist `digest_run` and `news_item` records in Neon PostgreSQL
  └── 8. Send formatted HTML digest email via Resend
```

---

## 🔑 Where All API Keys Live

All secret keys and credentials are set as environment variables on the backend (e.g., in `.env` or system environment variables). **Never hardcode keys in source code.**

| Key Variable | Purpose | Where to obtain |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `DATABASE_URL` | Neon PostgreSQL connection string | [neon.tech](https://neon.tech) |
| `SPRING_DATASOURCE_USERNAME` | Neon DB Username | [neon.tech](https://neon.tech) |
| `SPRING_DATASOURCE_PASSWORD` | Neon DB Password | [neon.tech](https://neon.tech) |
| `TINYFISH_API_KEY` | TinyFish Search & Fetch API key | [agent.tinyfish.ai](https://agent.tinyfish.ai) |
| `GROQ_API_KEY` | Groq LLM inference key | [console.groq.com](https://console.groq.com) |
| `RESEND_API_KEY` | Resend email sending key | [resend.com](https://resend.com) |
| `DIGEST_RECIPIENT_EMAIL` | Default recipient email address | Your email |
| `RESEND_FROM_EMAIL` | Sender email address (`onboarding@resend.dev` or custom domain) | Resend settings |

---

## ➕ How to Add a New Channel

You can add a new channel in two ways:

1. **Via the Frontend UI (Recommended)**:
   - Navigate to the frontend UI (`http://localhost:3000` or your Vercel deployment URL).
   - Click **"Create Channel"** or **"New Channel"**.
   - Fill in:
     - **Name**: e.g., `AI Agents`
     - **Topic Query**: e.g., `artificial intelligence autonomous agents LLM frameworks`
     - **Article Count**: `10` (range: 5–25)
     - **Delivery Time**: `08:00`
     - **Timezone**: `Asia/Kolkata` (or your local IANA timezone)
   - Click **Save**. The Spring Boot backend instantly registers the dynamic schedule trigger in-memory without requiring an application restart.

2. **Via Backend REST API (`POST /api/channels`)**:
   ```bash
   curl -X POST http://localhost:8080/api/channels \
     -H "Content-Type: application/json" \
     -d '{
       "name": "DevOps & Cloud",
       "topicQuery": "Kubernetes cloud native serverless Docker developments",
       "articleCount": 10,
       "cronTime": "09:00",
       "timezone": "Asia/Kolkata",
       "isActive": true
     }'
   ```

---

## 🚀 How to Redeploy After Code Changes

When you push updates to the backend code, follow this standard deployment procedure on the Oracle Cloud VM:

1. **Build the production JAR locally or on the VM**:
   ```bash
   cd backend
   ./mvnw clean package -DskipTests
   ```

2. **Upload the JAR to your Oracle VM** (if building locally):
   ```bash
   scp target/backend-0.0.1-SNAPSHOT.jar ubuntu@<VM_PUBLIC_IP>:/home/ubuntu/beat/backend/target/backend-0.0.1-SNAPSHOT.jar
   ```

3. **Restart the `systemd` service on the VM**:
   ```bash
   ssh ubuntu@<VM_PUBLIC_IP> "sudo systemctl restart beat-backend"
   ```

4. **Verify service status and logs**:
   ```bash
   sudo systemctl status beat-backend
   sudo journalctl -u beat-backend -f -n 100
   ```

---

## 📧 Resend → Brevo Fallback Path (Phase 6 Documented Swap)

Beat uses **Resend** as its primary email delivery service (3,000 free emails/month). If domain verification or sending limits require a fallback, you can easily swap to **Brevo** (300 free emails/day):

1. **Sign up for Brevo** at [brevo.com](https://brevo.com) and generate an SMTP / REST API key.
2. In `EmailService.java`, replace the Resend API endpoint `https://api.resend.com/emails` with Brevo's endpoint `https://api.brevo.com/v3/smtp/email`.
3. Update the request payload structure:
   - Resend JSON: `{"from": "...", "to": ["..."], "subject": "...", "html": "..."}`
   - Brevo JSON: `{"sender": {"email": "..."}, "to": [{"email": "..."}], "subject": "...", "htmlContent": "..."}`
   - Header: replace `Authorization: Bearer RESEND_API_KEY` with `api-key: BREVO_API_KEY`.
4. Update `.env` with your `BREVO_API_KEY` and restart `beat-backend`.

---

## ⚙️ Setting up `systemd` Service on Oracle VM

Copy `backend/systemd/beat-backend.service` to `/etc/systemd/system/beat-backend.service`:

```bash
sudo cp backend/systemd/beat-backend.service /etc/systemd/system/beat-backend.service
sudo systemctl daemon-reload
sudo systemctl enable beat-backend
sudo systemctl start beat-backend
```

---

## 💻 Local Development Setup

1. **Clone Repository**:
   ```bash
   git clone https://github.com/kaiizer777/beat.git
   cd beat
   ```

2. **Backend**:
   ```bash
   cd backend
   cp .env.example .env  # Configure your credentials
   ./mvnw spring-boot:run
   ```

3. **Frontend**:
   ```bash
   cd frontend
   cp .env.local.example .env.local  # Set NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
   npm install
   npm run dev
   ```

---

## 📊 Reliability & Daily Limits Tracking

- **Groq API Limit**: Groq free tier imposes a strict ceiling of **1,000 Requests Per Day (RPD)**. Beat uses a dedicated `GroqUsageTracker` component to log and alert if daily calls reach 80% (800 RPD) or 90% (900 RPD) of the quota.
- **Transient Retries**: Transient network errors on TinyFish and Groq calls automatically retry with exponential backoff so temporary network blips do not fail a channel run.
- **Overlap Protection**: Concurrent executions for the same channel are automatically blocked while a run is in progress.