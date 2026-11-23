package com.laibin.sugarinventory.dt50;

import org.junit.Test;
import static org.junit.Assert.*;

public class AppUpdateReleaseTest {
    private static final String CERT = "a".repeat(64);
    private AppUpdateRelease release(String url) {
        return new AppUpdateRelease(10, "0.3.2", 23, "com.laibin.sugarinventory.dt50",
                CERT, url, "更新说明", "https://ccgl.site");
    }
    @Test public void comparesVersionCodesAndCompatibility() {
        AppUpdateRelease r = release("https://ccgl.site/app-updates/android/app-10.apk");
        assertTrue(r.newerThan(9));
        assertFalse(r.newerThan(10));
        assertFalse(r.newerThan(11));
        assertTrue(r.compatibleWith("com.laibin.sugarinventory.dt50", CERT.toUpperCase(), 23));
        assertFalse(r.compatibleWith("com.laibin.sugarinventory.dt50.debug", CERT, 35));
        assertFalse(r.compatibleWith("com.laibin.sugarinventory.dt50", "b".repeat(64), 35));
        assertFalse(r.compatibleWith("com.laibin.sugarinventory.dt50", CERT, 22));
    }
    @Test public void rejectsUntrustedOrAmbiguousDownloadLocations() {
        String[] urls = {
            "http://ccgl.site/app-updates/android/app.apk",
            "https://evil.example/app-updates/android/app.apk",
            "https://ccgl.site.evil.example/app-updates/android/app.apk",
            "https://user@ccgl.site/app-updates/android/app.apk",
            "https://ccgl.site:444/app-updates/android/app.apk",
            "https://ccgl.site/app-updates/android/../app.apk",
            "https://ccgl.site/app-updates/android/%2e%2e/app.apk",
            "https://ccgl.site/app-updates/android/app.apk?redirect=evil",
            "https://ccgl.site/app-updates/android/app.apk#fragment"
        };
        for (String url : urls) assertThrows(IllegalArgumentException.class, () -> release(url));
    }
    @Test public void rejectsInvalidReleaseMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new AppUpdateRelease(0, "0", 23,
                "pkg", CERT, "https://ccgl.site/app-updates/android/app.apk", "", "https://ccgl.site"));
        assertThrows(IllegalArgumentException.class, () -> new AppUpdateRelease(10, "0.3.2", 23,
                "pkg", "invalid", "https://ccgl.site/app-updates/android/app.apk", "", "https://ccgl.site"));
    }
}
