package iwo.wintech.ngnfincalc.platform.tenancy;

import iwo.wintech.ngnfincalc.shared.error.ErrorCode;
import iwo.wintech.ngnfincalc.shared.error.RequestException;

import java.util.Map;

public final class BrandContext {

    private static final ThreadLocal<String> BRAND = new ThreadLocal<>();

    private BrandContext() {}

    public static void set(String brand) {
        BRAND.set(brand);
    }

    public static String get() {
        String brand = BRAND.get();
        if (brand == null) {
            throw new RequestException("Missing brand context (X-App-Brand header required)",
                    ErrorCode.INVALID_INPUT, Map.of());
        }
        return brand;
    }

    public static void clear() {
        BRAND.remove();
    }
}
