package dev.che1s.phantomcreeper.model;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.BlueprintAnimation;
import com.ticxo.modelengine.api.animation.handler.AnimationHandler;
import com.ticxo.modelengine.api.animation.property.IAnimationProperty;
import com.ticxo.modelengine.api.animation.property.SimpleProperty;
import com.ticxo.modelengine.api.entity.Hitbox;
import com.ticxo.modelengine.api.entity.data.BukkitEntityData;
import com.ticxo.modelengine.api.events.ModelSetupEvent;
import com.ticxo.modelengine.api.generator.blueprint.ModelBlueprint;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.joml.Vector3fc;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * 封装 ModelEngine 4.1.1 的兼容性反射，并缓存字段查找结果。
 */
final class ModelEngineReflection {
    private static final ClassValue<Map<String, Field>> FIELDS = new ClassValue<>() {
        @Override
        protected Map<String, Field> computeValue(Class<?> type) {
            Map<String, Field> fields = new HashMap<>();
            for (String name : new String[]{"models", "cachedModels",
                    "lastHitboxOverride", "properties"}) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    fields.put(name, field);
                } catch (NoSuchFieldException ignored) {
                    // 当前类型没有该字段，由调用方决定是否回退。
                }
            }
            return Map.copyOf(fields);
        }
    };

    private ModelEngineReflection() {
    }

    @SuppressWarnings("unchecked")
    static void addModelWithoutEvent(
            ModeledEntity modeled,
            ActiveModel active) throws ReflectiveOperationException {
        ModelBlueprint blueprint = active.getBlueprint();
        active.setRemoved(false);
        active.setModeledEntity(modeled);
        active.generateModel();
        active.setMainHitbox(true);

        ((Map<String, ActiveModel>) field(modeled.getClass(), "models")
                .get(modeled)).put(blueprint.getName(), active);
        field(modeled.getClass(), "cachedModels").set(modeled, null);
        field(modeled.getClass(), "lastHitboxOverride").set(modeled, active);

        Hitbox mainHitbox = blueprint.getMainHitbox();
        if (mainHitbox != null) {
            Vector3fc modelScale = active.getScale();
            Hitbox cullHitbox = scaledHitbox(mainHitbox, modelScale);
            modeled.getBase().getData().setCullHitbox(cullHitbox);
            if (modeled.getBase().getData() instanceof BukkitEntityData data) {
                data.setHitbox(scaledHitbox(mainHitbox, active.getHitboxScale()));
            }
        }
        ModelEngineAPI.callEvent(new ModelSetupEvent(modeled, active));
    }

    @SuppressWarnings("unchecked")
    static IAnimationProperty startAnimation(
            ActiveModel active,
            String name,
            boolean override) {
        AnimationHandler handler = active.getAnimationHandler();
        IAnimationProperty property = handler.playAnimation(name, 0.0, 0.0, 1.0, override);
        if (property != null) {
            return property;
        }
        BlueprintAnimation blueprintAnimation = active.getBlueprint().getAnimationOrRef(name);
        if (blueprintAnimation == null) {
            return null;
        }
        property = new SimpleProperty(active, blueprintAnimation, 0.0, 0.0, 1.0);
        try {
            ((Map<String, IAnimationProperty>) field(handler.getClass(), "properties")
                    .get(handler)).put(property.getName(), property);
            return property;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = FIELDS.get(type).get(name);
        if (field == null) {
            throw new NoSuchFieldException(type.getName() + "#" + name);
        }
        return field;
    }

    private static Hitbox scaledHitbox(Hitbox hitbox, Vector3fc scale) {
        return new Hitbox(
                hitbox.getWidth() * scale.x(),
                hitbox.getHeight() * scale.y(),
                hitbox.getDepth() * scale.z(),
                hitbox.getEyeHeight() * scale.y()
        );
    }
}
