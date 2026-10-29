package io.github.op7418.guizangtv;

import org.junit.Test;
import static org.junit.Assert.*;

public class AerialPolicyTest {
    @Test public void catalogueRequiresAppleHttpsWithoutCredentialsOrCustomPort() {
        assertTrue(AerialPolicy.allowedUrl("https://sylvan.apple.com/Videos/film.mov"));
        for (String url : new String[]{"http://sylvan.apple.com/film.mov", "https://evil.example/film.mov",
                "https://sylvan.apple.com.evil.example/film.mov", "https://user@sylvan.apple.com/film.mov",
                "https://sylvan.apple.com:8443/film.mov", "not a URL"})
            assertFalse(url, AerialPolicy.allowedUrl(url));
    }
    @Test public void playbackFallsBackAfterThreeFailures() {
        assertFalse(AerialPolicy.exhausted(1));
        assertFalse(AerialPolicy.exhausted(2));
        assertTrue(AerialPolicy.exhausted(3));
    }
    @Test public void aerialIsAnAdditionalModeAndPreservesExistingValues() {
        for (int mode = 0; mode <= 3; mode++) assertEquals(mode, AppearanceValues.normalize("background", mode));
        assertEquals(0, AppearanceValues.defaultValue("background"));
        assertEquals(0, AppearanceValues.normalize("background", 4));
    }
}
