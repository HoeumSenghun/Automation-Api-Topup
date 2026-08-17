/**
 * Automates the daily topup test flow:
 *   1) Init    -> send requestID, get raw (encrypted) token
 *   2) RSA     -> decrypt token with private key, append PIN, re-encrypt with public key
 *   3) Confirm -> send final token, get confirm result
 *   4) Check   -> send requestID, get transaction status
 *
 * Each run sends exactly one init body (PINLESS topup or PINCODE), then RSA, confirm, check.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.load(args);
        Merchant merchant = config.merchant;
        ApiClient apiClient = new ApiClient(merchant.baseUrl, merchant.authorizationHeader(), config.language);
        RefIdGenerator refIdGenerator = new RefIdGenerator(
                config.projectDir, merchant.code, config.service);

        RefIdGenerator.Issue issue = refIdGenerator.next();
        String refId = issue.refId;
        System.out.println("Using merchant: " + merchant.code);
        System.out.println("Using env: .env");
        System.out.println("Using service: " + config.service.label);
        if (config.service == ServiceType.PINLESS) {
            System.out.println("Using amount: " + config.transAmount + " " + config.currency);
        } else {
            System.out.println("Using operator: " + config.networkOperator + " pinCodeId=" + config.pinCodeId);
        }
        if (issue.alreadyUsedToday.isEmpty()) {
            System.out.println("Today used refIds so far: (none)");
        } else {
            System.out.println("Today used refIds so far: " + String.join(", ", issue.alreadyUsedToday));
        }
        System.out.println("Using refId: " + refId);

        String initBody = config.buildInitBody(refId);
        printJson("INIT REQUEST", initBody);
        ApiResult initResult = apiClient.post(merchant.initPath, initBody);
        printJson("INIT RESPONSE HTTP " + initResult.httpStatus, initResult.body);
        failIfError("INIT", initResult);

        String rawToken = JsonField.extract(initResult.body, "txPaymentTokenId");
        if (rawToken == null || rawToken.isBlank()) {
            throw new StepFailedException("INIT", "Response did not contain 'txPaymentTokenId'. Cannot continue.");
        }

        String finalToken;
        try {
            finalToken = RsaTool.buildFinalToken(merchant, rawToken);
        } catch (Exception e) {
            throw new StepFailedException("RSA", "Failed to build final token: " + e.getMessage());
        }
        System.out.println("\n=== RSA ===");
        System.out.println("Final token built OK");

        String confirmBody = "{\"txPaymentTokenId\":\"" + finalToken + "\"}";
        printJson("CONFIRM REQUEST", confirmBody);
        ApiResult confirmResult = apiClient.post(merchant.confirmPath, confirmBody);
        printJson("CONFIRM RESPONSE HTTP " + confirmResult.httpStatus, confirmResult.body);
        failIfError("CONFIRM", confirmResult);

        String checkBody = "{\"refId\":\"" + refId + "\"}";
        printJson("CHECK REQUEST", checkBody);
        ApiResult checkResult = apiClient.post(merchant.checkPath, checkBody);
        printJson("CHECK RESPONSE HTTP " + checkResult.httpStatus, checkResult.body);
        failIfError("CHECK", checkResult);

        System.out.println("\nALL STEPS PASSED for merchant " + merchant.code
                + " service " + config.service.label + " refId " + refId);
    }

    private static void printJson(String title, String json) {
        System.out.println("\n=== " + title + " ===");
        System.out.println(JsonPretty.format(json));
    }

    /**
     * Stops the whole run immediately if a step failed.
     * Checks HTTP status and the API's own "status" field (success is "0").
     */
    private static void failIfError(String stepName, ApiResult result) {
        if (result.httpStatus < 200 || result.httpStatus >= 300) {
            throw new StepFailedException(stepName, "HTTP error " + result.httpStatus + ":\n"
                    + JsonPretty.format(result.body));
        }
        String status = JsonField.extract(result.body, "status");
        if (status != null && !status.equals("0")) {
            String message = JsonField.extract(result.body, "message");
            throw new StepFailedException(stepName,
                    "API returned status=" + status + (message != null ? " (" + message + ")" : ""));
        }
    }
}
