package com.cernecommerce.adapter.out.email;

/** User agent em texto que a pessoa reconhece, como "Chrome no Windows". */
final class DeviceLabel {

    private DeviceLabel() {
    }

    /** {@code null} quando o navegador não é conhecido: o e-mail omite a linha. */
    static String of(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String browser = browser(userAgent);
        if (browser == null) {
            return null;
        }
        String system = system(userAgent);
        return system == null ? browser : browser + " no " + system;
    }

    // A ordem importa: Edge e Opera também dizem Chrome, e o Chrome também diz Safari.
    private static String browser(String userAgent) {
        if (userAgent.contains("Edg")) return "Edge";
        if (userAgent.contains("OPR/")) return "Opera";
        if (userAgent.contains("SamsungBrowser")) return "Samsung Internet";
        if (userAgent.contains("Firefox") || userAgent.contains("FxiOS")) return "Firefox";
        if (userAgent.contains("Chrome") || userAgent.contains("CriOS")) return "Chrome";
        if (userAgent.contains("Safari")) return "Safari";
        return null;
    }

    // A ordem importa: o iPhone também diz Mac OS X, e o Android também diz Linux.
    private static String system(String userAgent) {
        if (userAgent.contains("iPhone")) return "iPhone";
        if (userAgent.contains("iPad")) return "iPad";
        if (userAgent.contains("Android")) return "Android";
        if (userAgent.contains("Windows")) return "Windows";
        if (userAgent.contains("Mac OS X")) return "Mac";
        if (userAgent.contains("Linux")) return "Linux";
        return null;
    }
}
