package io.github.op7418.guizangtv;

import java.net.URI;

/** Shared bounds for network playback. No cleartext fallback or unbounded retries. */
final class AerialPolicy {
    static final int STALL_MS = 8_000;
    static final int MAX_FAILURES = 3;
    static final int BUFFER_BYTES = 24 * 1024 * 1024;
    static boolean allowedUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equals(uri.getScheme()) && "sylvan.apple.com".equals(uri.getHost())
                    && uri.getUserInfo() == null && uri.getPort() == -1;
        } catch (IllegalArgumentException invalid) { return false; }
    }
    static boolean exhausted(int failures) { return failures >= MAX_FAILURES; }
}
