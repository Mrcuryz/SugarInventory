package com.laibin.sugarinventory.dt50;

import java.net.URI;

/** Validates the public update feed before it can produce a download action. */
final class AppUpdateRelease {
    final int versionCode;
    final String versionName;
    final int minSdk;
    final String packageName;
    final String certificateSha256;
    final String downloadUrl;
    final String notes;

    AppUpdateRelease(int code, String name, int sdk, String pkg, String certificate,
                     String url, String notes, String serverUrl) {
        if (code <= 0 || sdk < 23 || name == null || name.trim().isEmpty() || name.length() > 40
                || pkg == null || certificate == null || !certificate.matches("[a-fA-F0-9]{64}")
                || notes == null || notes.length() > 2000) {
            throw new IllegalArgumentException("Invalid release metadata");
        }
        URI server = URI.create(serverUrl);
        URI download = URI.create(url);
        if (!"https".equalsIgnoreCase(server.getScheme()) || server.getHost() == null
                || !"https".equalsIgnoreCase(download.getScheme())
                || !server.getHost().equalsIgnoreCase(download.getHost())
                || port(server) != port(download) || download.getUserInfo() != null
                || download.getQuery() != null || download.getFragment() != null
                || !download.getRawPath().matches("/app-updates/android/[A-Za-z0-9_-]+\\.apk")) {
            throw new IllegalArgumentException("Invalid release URL");
        }
        versionCode = code;
        versionName = name;
        minSdk = sdk;
        packageName = pkg;
        certificateSha256 = certificate;
        downloadUrl = download.toASCIIString();
        this.notes = notes;
    }

    boolean newerThan(int installedCode) { return versionCode > installedCode; }

    boolean compatibleWith(String installedPackage, String installedCertificate, int sdk) {
        return packageName.equals(installedPackage)
                && certificateSha256.equalsIgnoreCase(installedCertificate) && sdk >= minSdk;
    }

    private static int port(URI uri) { return uri.getPort() == -1 ? 443 : uri.getPort(); }
}
