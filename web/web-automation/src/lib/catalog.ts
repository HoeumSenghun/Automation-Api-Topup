export const CURRENCY = "USD";

export const OPERATORS = [
  { code: "METFONE", label: "Metfone" },
] as const;

/** PINCODE ids from the Metfone catalog. Value is USD. */
export const PINCODE_OPTIONS = [
  { id: "1", value: 1 },
  { id: "2", value: 2 },
  { id: "5", value: 5 },
  { id: "10", value: 10 },
  { id: "20", value: 20 },
  { id: "50", value: 50 },
] as const;

/** PINLESS presets. Spec: 1.5 USD, 1 USD, and integers 2–50 USD. USD only. */
export const PINLESS_PRESETS = ["1.5", "1", "2", "5", "10", "20", "50"] as const;

export const PINLESS_INTEGER_MIN = 1;
export const PINLESS_INTEGER_MAX = 50;

export function isValidPinlessAmount(raw: string): boolean {
  const value = raw.trim();
  if (value === "1.5") {
    return true;
  }
  if (!/^\d+$/.test(value)) {
    return false;
  }
  const n = Number(value);
  return n >= PINLESS_INTEGER_MIN && n <= PINLESS_INTEGER_MAX;
}

export function isValidPinCodeId(id: string): boolean {
  return PINCODE_OPTIONS.some((option) => option.id === id);
}
