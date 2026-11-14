package dev.che1s.phantomcreeper.lang;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocaleResolverTest {
    @Test
    void acceptsLanguageFileNamesAndLegacyCodes() {
        assertEquals("zh_cn", LocaleResolver.knownLocale("zh_cn.yml"));
        assertEquals("zh_tw", LocaleResolver.knownLocale("zh-TW"));
        assertEquals("en_us", LocaleResolver.knownLocale("en_US.yml"));
        assertEquals("ja", LocaleResolver.knownLocale("ja_JP"));
        assertEquals("ko", LocaleResolver.knownLocale("ko-KR"));
        assertEquals("de", LocaleResolver.knownLocale("de_DE"));
        assertEquals("fr", LocaleResolver.knownLocale("fr_FR"));
        assertEquals("es", LocaleResolver.knownLocale("es_ES"));
        assertEquals("ru", LocaleResolver.knownLocale("ru_RU"));
    }

    @Test
    void autoDetectsServerLocaleAndFallsBackToEnglish() {
        assertEquals("ja", LocaleResolver.detectServerLocale(
                Locale.forLanguageTag("ja-JP"), Map.of()));
        assertEquals("zh_tw", LocaleResolver.detectServerLocale(
                Locale.forLanguageTag("zh-Hant-HK"), Map.of()));
        assertEquals("de", LocaleResolver.detectServerLocale(
                Locale.ROOT, Map.of("LC_ALL", "de_DE.UTF-8")));
        assertEquals("en_us", LocaleResolver.detectServerLocale(
                Locale.ROOT, Map.of("LANG", "C.UTF-8")));
        assertEquals("en_us", LocaleResolver.detectServerLocale(
                Locale.forLanguageTag("pt-BR"), Map.of()));
    }

    @Test
    void environmentDetectionIgnoresEncodingAndModifiers() {
        assertEquals("zh_cn", LocaleResolver.fromEnvironment("zh_CN.UTF-8"));
        assertEquals("fr", LocaleResolver.fromEnvironment("fr_FR@euro"));
        assertNull(LocaleResolver.fromEnvironment("POSIX"));
    }

    @Test
    void autoAndCustomFileNamesAreRecognized() {
        assertTrue(LocaleResolver.isAuto("AUTO"));
        assertTrue(LocaleResolver.isAuto(" AUTO "));
        assertTrue(LocaleResolver.isSafeCustomName("custom_locale.yml"));
        assertFalse(LocaleResolver.isSafeCustomName("../custom.yml"));
        assertFalse(LocaleResolver.isAuto("zh_cn.yml"));
    }
}
