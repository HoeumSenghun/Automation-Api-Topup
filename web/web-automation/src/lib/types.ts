export type ServiceKind = "PINLESS" | "PINCODE";
/** Where to stop: Init only, through Confirm, or include Check. */
export type StopAt = "INIT" | "CONFIRM" | "CHECK";

export type MerchantSecrets = {
  code: string;
  baseUrl: string;
  pin: string;
  apiKey: string;
  privateKey: string;
  publicKey: string;
  initPath: string;
  confirmPath: string;
  checkPath: string;
};

export type RunRequest = {
  merchantCode: string;
  service: ServiceKind;
  transAmount?: string;
  currency?: string;
  customerPhoneNumber?: string;
  networkOperator?: string;
  pinCodeId?: string;
  refId?: string;
  dryRun?: boolean;
  language?: string;
  secret?: string;
  /** INIT = init only. CONFIRM = Init → RSA → Confirm. CHECK = also Check. */
  stopAt?: StopAt;
};

export type StepName = "INIT" | "RSA" | "CONFIRM" | "CHECK";

export type StepResult = {
  name: StepName;
  ok: boolean;
  httpStatus?: number;
  request?: unknown;
  response?: unknown;
  detail?: string;
  error?: string;
  rsa?: RsaProcess;
};

export type RsaProcess = {
  decrypted: string;
  combined: string;
  finalToken: string;
};

export type RunResponse = {
  ok: boolean;
  dryRun?: boolean;
  merchant: string;
  service: ServiceKind;
  stopAt: StopAt;
  refId: string;
  url: string;
  paidTid?: string;
  steps: StepResult[];
  error?: { step: StepName | "CONFIG"; message: string };
};
