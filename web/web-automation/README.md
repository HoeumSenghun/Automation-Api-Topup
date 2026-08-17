# Web topup test runner

Next.js UI for the same Init → RSA → Confirm → Check flow as the Java CLI. Secrets never go to the browser.

## Local run

From this folder (`web/web-automation`):

```bash
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000).

Credentials load in this order:

1. `MERCHANT_DOTENV` (Vercel: paste the **repo-root** `.env` contents)
2. `.env.local` in this folder (optional override)
3. `.env` in this folder (optional)
4. The Java `.env` at the **repo root** — this is what local `npm run dev` uses

Use **one** credentials template: repo-root `.env.example` → copy to repo-root `.env`.
Do not put real keys in `.env.example` files.

## How to test

1. Choose a merchant (from `.env`)
2. Choose **PINLESS** or **PINCODE**
3. PINLESS: amount chips (1.5, 1, 2–50 USD) + customer phone
4. PINCODE: Metfone + pin id 1 / 2 / 5 / 10 / 20 / 50
5. Optional: **Show advanced** → dry run (inspect the init body, no API call) or a custom `refId`
6. **Run full flow** and read each step’s JSON on the right

Phone is remembered in `sessionStorage` for the tab only.

## Vercel (this folder only)

The GitHub repo also contains Java. Deploy **only** this Next.js app:

1. Import the same GitHub repo in Vercel.
2. Set **Root Directory** to `web/web-automation`.
3. Framework: **Next.js**. Do not set a custom output directory.
4. Add env vars (Vercel does not get the gitignored root `.env`):
   - `MERCHANT_DOTENV` — paste the whole repo-root `.env` file
   - optional `RUN_SECRET`
5. Enable Deployment Protection.

To skip builds when only Java files changed, set **Ignored Build Step** to:

```bash
git diff --quiet HEAD^ HEAD -- web/web-automation
```


## Security

PIN, API key, and RSA keys are read only in Route Handlers (`/api/merchants`, `/api/run`). The merchants endpoint returns codes only, never secrets.
