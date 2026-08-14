import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads shared run settings from config.properties and merchant credentials
 * from a single .env (shared baseUrl plus {merchantCode}.pin/apiKey/keys).
 */
final class AppConfig {
    private static final Pattern ENV_PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");
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

    private AppConfig(Properties props, String activeMerchantCode, String productRaw, Path projectDir,
                      Properties dotenv) {
        this.projectDir = projectDir;
        this.language = optional(props, "language", "en");
        this.product = ProductType.parse(productRaw);
        this.merchants = loadMerchants(dotenv);
        this.merchant = merchants.get(activeMerchantCode);
        if (this.merchant == null) {
            throw new IllegalStateException(
                    "Unknown merchant '" + activeMerchantCode + "'. Add it to .env (merchants=...). Known: "
                            + merchants.keySet());
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
        Path projectDir = path.toAbsolutePath().getParent();
        Properties dotenv = loadDotEnv(projectDir.resolve(".env"));
        Properties props = loadProperties(path, dotenv);
        String merchant = arg(args, 0, required(props, "activeMerchant"));
        String product = arg(args, 1, required(props, "product"));
        return new AppConfig(props, merchant, product, projectDir, dotenv);
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

    private static Map<String, Merchant> loadMerchants(Properties dotenv) {
        List<String> codes = merchantCodes(dotenv);
        if (codes.isEmpty()) {
            throw new IllegalStateException(
                    "No merchants in .env. Set merchants=Code1,Code2 and {code}.pin/apiKey/privateKey/publicKey.");
        }
        Map<String, Merchant> result = new LinkedHashMap<>();
        for (String code : codes) {
            result.put(code, loadMerchant(code, dotenv));
        }
        return result;
    }

    private static List<String> merchantCodes(Properties dotenv) {
        String listed = dotenv.getProperty("merchants");
        if (listed != null && !listed.isBlank()) {
            List<String> codes = new ArrayList<>();
            for (String part : listed.split(",")) {
                String code = part.trim();
                if (!code.isEmpty()) {
                    codes.add(code);
                }
            }
            return codes;
        }
        Set<String> codes = new LinkedHashSet<>();
        for (String name : dotenv.stringPropertyNames()) {
            int dot = name.lastIndexOf('.');
            if (dot <= 0) {
                continue;
            }
            String field = name.substring(dot + 1);
            if (field.equals("pin") || field.equals("apiKey")) {
                codes.add(name.substring(0, dot));
            }
        }
        return new ArrayList<>(codes);
    }

    private static Merchant loadMerchant(String code, Properties dotenv) {
        String baseUrl = firstNonBlank(dotenv.getProperty(code + ".baseUrl"), dotenv.getProperty("baseUrl"));
        if (baseUrl == null) {
            throw new IllegalStateException("Missing baseUrl (shared or " + code + ".baseUrl) in .env");
        }
        return new Merchant(
                code,
                baseUrl,
                required(dotenv, code + ".pin"),
                required(dotenv, code + ".apiKey"),
                required(dotenv, code + ".privateKey"),
                required(dotenv, code + ".publicKey"),
                optional(dotenv, code + ".initPath", "/" + code + "/telco/init"),
                optional(dotenv, code + ".confirmPath", "/" + code + "/telco/confirm"),
                optional(dotenv, code + ".checkPath", "/" + code + "/trans/check")
        );
    }

    private static Properties loadDotEnv(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("Missing .env.");
        }
        Properties trimmed = new Properties();
        copyNonBlank(readProperties(path), trimmed);
        return trimmed;
    }

    /** File values overlay .env. Blank keys and ${name} placeholders use .env. */
    private static Properties loadProperties(Path path, Properties dotenv) throws IOException {
        Properties file = readProperties(path);
        Properties merged = new Properties();
        copyNonBlank(dotenv, merged);
        for (String name : file.stringPropertyNames()) {
            String raw = file.getProperty(name);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            merged.setProperty(name, substitute(raw.trim(), dotenv));
        }
        return merged;
    }

    private static Properties readProperties(Path path) throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        }
        return props;
    }

    private static void copyNonBlank(Properties source, Properties target) {
        if (source == null) {
            return;
        }
        for (String name : source.stringPropertyNames()) {
            String value = source.getProperty(name);
            if (value != null && !value.isBlank()) {
                target.setProperty(name, value.trim());
            }
        }
    }

    private static String substitute(String value, Properties dotenv) {
        Matcher matcher = ENV_PLACEHOLDER.matcher(value);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String replacement = dotenv != null ? dotenv.getProperty(key) : null;
            if (replacement == null || replacement.isBlank()) {
                throw new IllegalStateException("Missing .env key: " + key);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement.trim()));
        }
        matcher.appendTail(out);
        return out.toString();
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

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
