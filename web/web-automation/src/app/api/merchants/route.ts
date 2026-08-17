import { NextResponse } from "next/server";
import { loadEnv } from "@/lib/env";
import { CURRENCY, OPERATORS, PINCODE_OPTIONS, PINLESS_PRESETS } from "@/lib/catalog";

export const dynamic = "force-dynamic";

export async function GET() {
  try {
    const env = loadEnv();
    return NextResponse.json({
      merchants: [...env.merchants.keys()],
      authRequired: env.authRequired,
      configured: env.merchants.size > 0,
      catalog: {
        currency: CURRENCY,
        operators: OPERATORS,
        pincode: PINCODE_OPTIONS,
        pinlessPresets: PINLESS_PRESETS,
      },
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Failed to load merchants";
    return NextResponse.json({ merchants: [], configured: false, error: message }, { status: 500 });
  }
}
