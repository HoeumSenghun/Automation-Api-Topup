import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Loads shared run settings from config.properties and one env file per merchant
 * from env/{merchantCode}.properties (baseUrl, pin, apiKey, privateKey, publicKey).
 */
final class AppConfig {
    final ProductType product;
    final String transAmount;
    final String currency;
    final String customerPhoneNumber;
    final String networkOperator;
    final String pinCodeId;
    final String language;
    final Map<String, Merchant> merchants;
    final Merchant merchant;
    final Path projectDir;

    private AppConfig(Properties props, String activeMerchantCode, String productRaw, Path projectDir) {
        this.projectDir = projectDir;
        this.language = optional(props, "language", "en");
        this.product = ProductType.parse(productRaw);
        this.merchants = loadMerchants(projectDir);
        this.merchant = merchants.get(activeMerchantCode);
        if (this.merchant == null) {
            throw new IllegalStateException(
                    "Unknown merchant '" + activeMerchantCode + "'. Add env/" + activeMerchantCode
                            + ".properties. Known: " + merchants.keySet());
        }

        if (this.product == ProductType.PINLESS) {
            this.transAmount = required(props, "transAmount");
            this.currency = required(props, "currency").toUpperCase();
            this.customerPhoneNumber = required(props, "customerPhoneNumber");
            this.networkOperator = null;
            this.pinCodeId = null;
        } else {
            this.networkOperator = required(props, "networkOperator");
            this.pinCodeId = required(props, "pinCodeId");
            this.transAmount = null;
            this.currency = null;
            this.customerPhoneNumber = optional(props, "customerPhoneNumber", null);
        }
    }

    static AppConfig load(String[] args) throws IOException {
        Path path = Paths.get("config.properties");
        if (!Files.exists(path)) {
            throw new IOException(
                    "Missing config.properties.");
        }
        Properties props = loadProperties(path);
        String merchant = arg(args, 0, required(props, "activeMerchant"));
        String product = arg(args, 1, required(props, "product"));
        return new AppConfig(props, merchant, product, path.toAbsolutePath().getParent());
    }

    String buildInitBody(String refId) {
        if (product == ProductType.PINCODE) {
            return "{"
                    + "\"serviceType\":\"PINCODE\","
                    + "\"networkOperator\":\"" + networkOperator + "\","
                    + "\"pinCodeId\":\"" + pinCodeId + "\","
                    + "\"refId\":\"" + refId + "\""
                    + "}";
        }
        return "{"
                + "\"serviceType\":\"TOPUP\","
                + "\"transAmount\":\"" + transAmount + "\","
                + "\"currency\":\"" + currency + "\","
                + "\"refId\":\"" + refId + "\","
                + "\"customerPhoneNumber\":\"" + customerPhoneNumber + "\""
                + "}";
    }

    private static Map<String, Merchant> loadMerchants(Path projectDir) {
        Path envDir = projectDir.resolve("env");
        if (!Files.isDirectory(envDir)) {
            throw new IllegalStateException(
                    "Missing env/ folder. Add env/{merchantCode}.properties");
        }
        Map<String, Merchant> result = new TreeMap<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(envDir, "*.properties")) {
            for (Path envFile : stream) {
                String fileName = envFile.getFileName().toString();
                String code = fileName.substring(0, fileName.length() - ".properties".length());
                result.put(code, loadMerchant(code, envFile));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read merchant env files in " + envDir, e);
        }
        if (result.isEmpty()) {
            throw new IllegalStateException(
                    "No merchant env files found. Add env/{merchantCode}.properties");
        }
        return new LinkedHashMap<>(result);
    }

    private static Merchant loadMerchant(String code, Path envFile) {
        Properties env;
        try {
            env = loadProperties(envFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + envFile, e);
        }
        return new Merchant(
                code,
                required(env, "baseUrl"),
                required(env, "pin"),
                required(env, "apiKey"),
                required(env, "privateKey"),
                required(env, "publicKey"),
                optional(env, "initPath", "/" + code + "/telco/init"),
                optional(env, "confirmPath", "/" + code + "/telco/confirm"),
                optional(env, "checkPath", "/" + code + "/trans/check")
        );
    }

    private static Properties loadProperties(Path path) throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        }
        return props;
    }

    private static String arg(String[] args, int index, String fallback) {
        if (args != null && args.length > index && args[index] != null && !args[index].isBlank()) {
            return args[index].trim();
        }
        return fallback;
    }

    private static String required(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required config key: " + key);
        }
        return value.trim();
    }

    private static String optional(Properties props, String key, String fallback) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
