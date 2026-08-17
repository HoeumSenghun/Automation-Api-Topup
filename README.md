# Automation API Topup

Java test runner for the merchant telco topup API. Each run executes one full flow:

1. **Init** — send a unique `refId` and get an encrypted payment token
2. **RSA** — decrypt the token with the merchant private key, append the PIN, re-encrypt with the public key
3. **Confirm** — send the final token
4. **Check** — look up the transaction by `refId`

The run stops at the first failed step (non-2xx HTTP or API `status` other than `0`).

## Requirements

- JDK 11 or later
- A merchant API host and credentials (not stored in this repo)

## Setup

Copy the example files. Do **not** commit the copies.

```bash
cp .env.example .env
cp config.properties.example config.properties
```

Fill in `.env` with credentials. Fill in `config.properties` with the merchant and service for this run.

## Configuration

### `.env` (secrets)

Shared host plus one block per merchant. Switch merchants in `config.properties` (`activeMerchant`), not by editing keys every time.

```
baseUrl=https://YOUR-HOST:PORT

merchants=ExampleTest,OtherMerchant

ExampleTest.pin=YOUR_PIN
ExampleTest.apiKey=YOUR_API_KEY
ExampleTest.privateKey=YOUR_PRIVATE_KEY_BASE64
ExampleTest.publicKey=YOUR_PUBLIC_KEY_BASE64

OtherMerchant.pin=YOUR_PIN
OtherMerchant.apiKey=YOUR_API_KEY
OtherMerchant.privateKey=YOUR_PRIVATE_KEY_BASE64
OtherMerchant.publicKey=YOUR_PUBLIC_KEY_BASE64
```

Required per merchant: `{code}.pin`, `{code}.apiKey`, `{code}.privateKey`, `{code}.publicKey`.

Optional per merchant:

| Key | Default |
|---|---|
| `{code}.baseUrl` | shared `baseUrl` |
| `{code}.initPath` | `/{code}/telco/init` |
| `{code}.confirmPath` | `/{code}/telco/confirm` |
| `{code}.checkPath` | `/{code}/trans/check` |

`config.properties` can also use `${name}` placeholders that resolve from `.env`.

### `config.properties` (run settings)

| Key | Purpose |
|---|---|
| `activeMerchant` | Merchant code listed in `.env` |
| `service` | `PINLESS` or `PINCODE` |
| `language` | Request language header (default `en`) |
| `transAmount`, `currency`, `customerPhoneNumber` | Used when `service=PINLESS` |
| `networkOperator`, `pinCodeId` | Used when `service=PINCODE` |

## Run

From the project root (so `config.properties` and `.env` are found):

```bash
javac -d out src/*.java
java -cp out Main
```

Override merchant and/or service from the command line:

```bash
java -cp out Main ExampleTest PINLESS
java -cp out Main ExampleTest PINCODE
```

## Services and refIds

| `service` | Init `serviceType` | refId pattern |
|---|---|---|
| `PINLESS` | `TOPUP` | `TOPUP` + `yyMMdd` + 4-digit sequence |
| `PINCODE` | `PINCODE` | `PINCODE` + `yyMMdd` + 4-digit sequence |

Used refIds for the current day are stored under `data/` so a later run does not reuse them. If the API still reports a duplicate `refId`, that id was already used on the server (for example from another machine). Advance the local sequence and retry.

## Project layout

```
src/                     Java CLI
web/web-automation/      Next.js UI (Vercel root directory)
config.properties.example
.env.example
```

Gitignored (never commit):

- `.env`
- `config.properties`
- `data/`
- `keys/`
- `out/`

## Secrets

Keep PINs, API keys, RSA keys, host URLs, and customer phone numbers out of git and out of chat logs. Use only the `*.example` files as templates.

## Web UI

A Next.js console lives in `web/web-automation`. Same API flow, with merchant / PINLESS / PINCODE pickers. See `web/web-automation/README.md`. Local `npm run dev` can reuse the Java `.env` at this repo root.

## Deploy only the web app on Vercel

The GitHub repo can stay as one project (Java + web). Vercel should **not** build Java. Point it at the Next.js folder only.

1. Push this repo to GitHub.
2. In [Vercel](https://vercel.com) → **Add New Project** → import this repository.
3. Before deploy, set:
   - **Framework Preset:** Next.js
   - **Root Directory:** `web/web-automation`  
     (click *Edit* next to Root Directory — this is the important step)
   - Leave Build Command / Output as the Next.js defaults
4. **Environment Variables** (Production + Preview). Vercel cannot read the gitignored root `.env`, so paste secrets here:
   - `MERCHANT_DOTENV` = full contents of your local `.env` (the merchant keys file)
   - optional `RUN_SECRET` = a password the UI will require
5. Deploy.

Optional, so Java-only commits do not trigger a web rebuild:  
**Project Settings → Git → Ignored Build Step:**

```bash
git diff --quiet HEAD^ HEAD -- web/web-automation
```

Also turn on **Deployment Protection** (Vercel Authentication) so the public URL cannot trigger real topups.

