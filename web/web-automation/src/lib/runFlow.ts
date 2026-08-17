import { CURRENCY, isValidPinCodeId, isValidPinlessAmount } from "./catalog";
import { authorizationHeader, apiMessage, apiStatus, paidTid, postJson, txPaymentTokenId } from "./http";
import { nextRefId } from "./refId";
import { buildFinalToken } from "./rsa";
import type { MerchantSecrets, RunRequest, RunResponse, StepResult, StopAt } from "./types";

function fail(
  response: RunResponse,
  step: StepResult["name"] | "CONFIG",
  message: string,
  extra?: Partial<StepResult>,
): RunResponse {
  if (step !== "CONFIG") {
    const last = response.steps.at(-1);
    if (last?.name === step) {
      last.ok = false;
      last.error = message;
    } else {
      response.steps.push({
        name: step,
        ok: false,
        error: message,
        ...extra,
      });
    }
  }
  response.ok = false;
  response.error = { step, message };
  return response;
}

function stepError(httpStatus: number, json: unknown): string | null {
  if (httpStatus < 200 || httpStatus >= 300) {
    return `HTTP ${httpStatus}`;
  }
  const status = apiStatus(json);
  if (status !== null && status !== "0") {
    const message = apiMessage(json);
    return `API status=${status}${message ? ` (${message})` : ""}`;
  }
  return null;
}

export function validateAndBuildInit(
  input: RunRequest,
  merchant: MerchantSecrets,
  refId: string,
): { body: Record<string, string> } | { error: string } {
  if (input.service === "PINCODE") {
    const networkOperator = input.networkOperator?.trim();
    const pinCodeId = input.pinCodeId?.trim();
    if (!networkOperator) {
      return { error: "networkOperator is required for PINCODE" };
    }
    if (!pinCodeId || !isValidPinCodeId(pinCodeId)) {
      return { error: "pinCodeId must be 1, 2, 5, 10, 20, or 50" };
    }
    return {
      body: {
        serviceType: "PINCODE",
        networkOperator,
        pinCodeId,
        refId,
      },
    };
  }
  const transAmount = input.transAmount?.trim();
  const customerPhoneNumber = input.customerPhoneNumber?.trim();
  if (!transAmount || !isValidPinlessAmount(transAmount)) {
    return { error: "PINLESS amount must be 1.5, 1, or an integer from 2 to 50 USD" };
  }
  if (!customerPhoneNumber) {
    return { error: "customerPhoneNumber is required for PINLESS" };
  }
  return {
    body: {
      serviceType: "TOPUP",
      transAmount,
      currency: (input.currency || CURRENCY).toUpperCase(),
      refId,
      customerPhoneNumber,
    },
  };
}

export async function runFlow(
  input: RunRequest,
  merchant: MerchantSecrets,
): Promise<RunResponse> {
  const refId = nextRefId(input.service, input.refId);
  const language = input.language?.trim() || "en";
  const stopAt: StopAt =
    input.stopAt === "INIT" || input.stopAt === "CHECK" ? input.stopAt : "CONFIRM";
  const auth = authorizationHeader(merchant.apiKey);
  const result: RunResponse = {
    ok: true,
    merchant: merchant.code,
    service: input.service,
    stopAt,
    refId,
    url: merchant.baseUrl,
    steps: [],
  };

  const built = validateAndBuildInit(input, merchant, refId);
  if ("error" in built) {
    return fail(result, "CONFIG", built.error);
  }

  if (input.dryRun) {
    result.dryRun = true;
    result.steps.push({
      name: "INIT",
      ok: true,
      request: built.body,
      detail: `Preview only. Would POST ${merchant.baseUrl}${merchant.initPath}`,
    });
    return result;
  }

  const init = await postJson(`${merchant.baseUrl}${merchant.initPath}`, auth, language, built.body);
  const initFail = stepError(init.httpStatus, init.json);
  result.steps.push({
    name: "INIT",
    ok: !initFail,
    httpStatus: init.httpStatus,
    request: built.body,
    response: init.json,
    error: initFail ?? undefined,
  });
  if (initFail) {
    return fail(result, "INIT", initFail);
  }
  if (stopAt === "INIT") {
    return result;
  }

  const rawToken = txPaymentTokenId(init.json);
  if (!rawToken) {
    return fail(result, "INIT", "Response did not contain txPaymentTokenId");
  }

  let rsaProcess;
  try {
    rsaProcess = buildFinalToken(rawToken, merchant.privateKey, merchant.publicKey, merchant.pin);
    result.steps.push({
      name: "RSA",
      ok: true,
      detail: "Decrypt token → append PIN → encrypt final token",
      rsa: rsaProcess,
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "RSA failed";
    return fail(result, "RSA", message);
  }

  const confirmBody = { txPaymentTokenId: rsaProcess.finalToken };
  const confirm = await postJson(
    `${merchant.baseUrl}${merchant.confirmPath}`,
    auth,
    language,
    confirmBody,
  );
  const confirmFail = stepError(confirm.httpStatus, confirm.json);
  result.steps.push({
    name: "CONFIRM",
    ok: !confirmFail,
    httpStatus: confirm.httpStatus,
    request: { txPaymentTokenId: rsaProcess.finalToken },
    response: confirm.json,
    error: confirmFail ?? undefined,
  });
  const confirmTid = paidTid(confirm.json);
  if (confirmTid) {
    result.paidTid = confirmTid;
  }
  if (confirmFail) {
    return fail(result, "CONFIRM", confirmFail);
  }
  if (stopAt === "CONFIRM") {
    return result;
  }

  const checkBody = { refId };
  const check = await postJson(`${merchant.baseUrl}${merchant.checkPath}`, auth, language, checkBody);
  const checkFail = stepError(check.httpStatus, check.json);
  result.steps.push({
    name: "CHECK",
    ok: !checkFail,
    httpStatus: check.httpStatus,
    request: checkBody,
    response: check.json,
    error: checkFail ?? undefined,
  });
  const checkTid = paidTid(check.json);
  if (checkTid) {
    result.paidTid = checkTid;
  }
  if (checkFail) {
    return fail(result, "CHECK", checkFail);
  }

  return result;
}
