# Web topup test runner

Next.js UI for the same Init → RSA → Confirm → Check flow as the Java CLI. Secrets never go to the browser.

## Local run

From this folder (`web/web-automation`):

```bash
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000). The UI is locked until you enter the 6-digit `ACCESS_PIN`.

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
   - `ACCESS_PIN` — 6 digits (login). You can also put `ACCESS_PIN=123456` as a line inside `MERCHANT_DOTENV`.
   - optional `RUN_SECRET`
5. Enable Deployment Protection.

To skip builds when only Java files changed, set **Ignored Build Step** to:

```bash
git diff --quiet HEAD^ HEAD -- web/web-automation
```


## Site login (6-digit PIN)

No user database. `src/proxy.ts` blocks every page and API except `/login` until a signed httpOnly cookie is set.

Set `ACCESS_PIN` (exactly 6 digits) in:

- `web/web-automation/.env.local` for local
- Vercel env `ACCESS_PIN`, or a line in `MERCHANT_DOTENV`

Wrong PIN is rate-limited. Cookie lasts 12 hours. Use **Sign out** to clear it.

## Security

Merchant PIN, API key, and RSA keys are read only in Route Handlers (`/api/merchants`, `/api/run`). The merchants endpoint returns codes only, never secrets. The site PIN never goes to the browser.
