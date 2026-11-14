package dev.che1s.phantomcreeper.config;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginResourceTest {
    @Test
    void packagedConfigContainsSafeExplosionDefaults() {
        Map<String, Object> root = yaml("config.yml");
        assertEquals("auto", root.get("language"));
        assertEquals(true, root.get("bstats-enabled"));
        Map<?, ?> combat = (Map<?, ?>) root.get("combat");
        Map<?, ?> explosion = (Map<?, ?>) combat.get("explosion");

        assertEquals(false, explosion.get("break-blocks"));
        assertEquals(30, combat.get("fuse-ticks"));
        assertEquals(7.0, combat.get("fuse-cancel-radius"));
        assertEquals(6.0, explosion.get("radius"));
        assertTrue(((Number) explosion.get("damage")).doubleValue() > 0.0);
        assertEquals(true, explosion.get("damage-falloff"));

        Map<?, ?> model = (Map<?, ?>) root.get("model");
        assertEquals("auto", model.get("engine"));
        assertEquals(true, model.get("modelengine-auto-install-blueprint"));

        Map<?, ?> naturalSpawn = (Map<?, ?>) root.get("natural-spawn");
        assertEquals(java.util.List.of("world"), naturalSpawn.get("worlds"));
        assertEquals(10, naturalSpawn.get("max-active"));
        assertEquals(20, naturalSpawn.get("max-tracked"));
        Map<?, ?> thresholdSpawn =
                (Map<?, ?>) naturalSpawn.get("phantom-threshold-spawn");
        assertEquals(32.0, thresholdSpawn.get("scan-radius"));
        assertEquals(2, ((java.util.List<?>) thresholdSpawn.get("triggers")).size());
    }

    @Test
    void allBundledTranslationsExistAndContainTheSameKeys() {
        Map<String, Object> chinese = yaml("lang/zh_cn.yml");
        Set<String> expected = flattenKeys(chinese, "");

        assertEquals("[幻翼苦力怕]</dark_aqua> <dark_gray>»</dark_gray> ",
                ((String) chinese.get("prefix")).substring("<dark_aqua>".length()));
        for (String language : java.util.List.of(
                "en_us", "zh_cn", "ja", "ko", "zh_tw", "de", "fr", "es", "ru")) {
            Map<String, Object> translations = yaml("lang/" + language + ".yml");
            assertEquals(expected, flattenKeys(translations, ""), language);
        }
    }

    @Test
    void pluginMetadataExposesPlayerStatsCommand() {
        Map<String, Object> root = yaml("plugin.yml");
        Map<?, ?> command = (Map<?, ?>) ((Map<?, ?>) root.get("commands"))
                .get("phantomcreeper");
        Map<?, ?> permissions = (Map<?, ?>) root.get("permissions");

        assertTrue(String.valueOf(command.get("usage")).contains("stats"));
        assertEquals(true, ((Map<?, ?>) permissions.get("phantomcreeper.stats"))
                .get("default"));
        assertEquals("op", ((Map<?, ?>) permissions.get("phantomcreeper.admin"))
                .get("default"));
        assertEquals(java.util.List.of("che1s"), root.get("authors"));
    }

    @Test
    void configCommentsContainChineseAndEnglish() {
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("config.yml")) {
            assertNotNull(stream);
            String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(text.contains("# 插件语言："));
            assertTrue(text.contains("# Plugin language:"));
            assertTrue(text.contains("# 渲染后端："));
            assertTrue(text.contains("# Renderer backend:"));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void modelContainsLoopingWingAnimation() {
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("models/bettermodel/phantom_creeper.bbmodel")) {
            assertNotNull(stream);
            var model = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            var animation = model.getAsJsonArray("animations").get(0).getAsJsonObject();

            assertEquals("flap", animation.get("name").getAsString());
            assertEquals("loop", animation.get("loop").getAsString());
            assertEquals(3, animation.getAsJsonObject("animators").size());
            assertEquals(2, model.getAsJsonArray("animations").size());
            assertTrue(model.getAsJsonArray("animations").asList().stream()
                    .anyMatch(element -> element.getAsJsonObject().get("name").getAsString()
                            .equals("death")));
            assertTrue(model.getAsJsonArray("groups").asList().stream()
                    .anyMatch(element -> element.getAsJsonObject().get("name").getAsString()
                            .equals("h_head")));
            assertEquals(1, model.getAsJsonArray("textures").size());
            assertTrue(model.getAsJsonArray("elements").asList().stream()
                    .anyMatch(element -> element.getAsJsonObject().get("type").getAsString()
                            .equals("bounding_box")));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void modelEngineBlueprintUsesVersionFiveHitboxGroup() {
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("models/modelengine/phantom_creeper.bbmodel")) {
            assertNotNull(stream);
            var model = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();

            assertEquals("5.0",
                    model.getAsJsonObject("meta").get("format_version").getAsString());
            assertTrue(model.getAsJsonArray("groups").asList().stream()
                    .anyMatch(element -> element.getAsJsonObject().get("name").getAsString()
                            .equals("hitbox")));
            assertTrue(model.getAsJsonArray("elements").asList().stream()
                    .allMatch(element -> element.getAsJsonObject().get("type").getAsString()
                            .equals("cube")));

            var death = model.getAsJsonArray("animations").asList().stream()
                    .map(element -> element.getAsJsonObject())
                    .filter(animation -> animation.get("name").getAsString().equals("death"))
                    .findFirst()
                    .orElseThrow();
            var bodyAnimator = death.getAsJsonObject("animators").entrySet().stream()
                    .map(Map.Entry::getValue)
                    .map(element -> element.getAsJsonObject())
                    .filter(animator -> animator.get("name").getAsString().equals("body"))
                    .findFirst()
                    .orElseThrow();
            var rotationXValues = bodyAnimator.getAsJsonArray("keyframes").asList().stream()
                    .map(element -> element.getAsJsonObject())
                    .filter(keyframe -> keyframe.get("channel").getAsString().equals("rotation"))
                    .flatMap(keyframe -> keyframe.getAsJsonArray("data_points").asList().stream())
                    .map(point -> point.getAsJsonObject().get("x").getAsString())
                    .toList();
            assertTrue(rotationXValues.contains("-82"));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private Map<String, Object> yaml(String path) {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream);
            return new Yaml().load(stream);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static Set<String> flattenKeys(Map<?, ?> map, String prefix) {
        Set<String> keys = new HashSet<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = prefix.isEmpty()
                    ? String.valueOf(entry.getKey())
                    : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof Map<?, ?> nested) {
                keys.addAll(flattenKeys(nested, key));
            } else {
                keys.add(key);
            }
        }
        return keys;
    }
}
