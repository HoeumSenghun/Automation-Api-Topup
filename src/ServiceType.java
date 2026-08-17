enum ServiceType {
    PINLESS("TOPUP", "PINLESS"),
    PINCODE("PINCODE", "PINCODE");

    final String refIdPrefix;
    final String label;
    final int seqWidth = 4;

    ServiceType(String refIdPrefix, String label) {
        this.refIdPrefix = refIdPrefix;
        this.label = label;
    }

    static ServiceType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Missing service. Use PINLESS or PINCODE.");
        }
        String value = raw.trim().toUpperCase().replace("-", "").replace("_", "");
        if (value.equals("PINLESS") || value.equals("TOPUP") || value.equals("TOPUPPINLESS")
                || value.equals("1")) {
            return PINLESS;
        }
        if (value.equals("PINCODE") || value.equals("TOPUPPINCODE") || value.equals("2")) {
            return PINCODE;
        }
        throw new IllegalStateException("Unknown service '" + raw + "'. Use PINLESS or PINCODE.");
    }
}
