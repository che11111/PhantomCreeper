package dev.che1s.phantomcreeper.model;

import com.ticxo.modelengine.api.entity.Dummy;
import com.ticxo.modelengine.api.generator.blueprint.BlueprintBone;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import com.ticxo.modelengine.api.model.bone.ModelBone;
import com.ticxo.modelengine.api.nms.entity.HitboxEntity;
import org.bukkit.Location;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;

/**
 * ModelEngine 模型实例及其真实载体桥接。
 */
final class ModelEngineAttachment implements ModelAttachment {
    private final ModeledEntity modeled;
    private final ActiveModel active;
    private final Dummy<LivingEntity> dummy;
    private final float patrolHeadPitch;
    private boolean patrolHeadLocked;

    ModelEngineAttachment(
            ModeledEntity modeled,
            ActiveModel active,
            Dummy<LivingEntity> dummy) {
        this.modeled = modeled;
        this.active = active;
        this.dummy = dummy;
        // ModelEngine 的头部俯仰会继承 body 骨骼预置角，盘旋时反向补偿。
        this.patrolHeadPitch = active.getBone("body")
                .map(ModelBone::getBlueprintBone)
                .map(BlueprintBone::getLocalRotation)
                .map(rotation -> -rotation.x)
                .orElse(0.0F);
        active.setModelRotationLocked(true);
    }

    @Override
    public int hitboxCount() {
        return active.isMainHitbox() ? 1 : 0;
    }

    @Override
    public void damageTint() {
        modeled.markHurt();
    }

    @Override
    public void setHeadRotation(float yaw, float pitch) {
        modeled.setYBodyRot(yaw);
        modeled.setYHeadRot(yaw);
        modeled.setXHeadRot(pitch);
        active.setLockedYBodyRot(yaw);
        active.setLockedYHeadRot(yaw);
        active.setLockedXHeadRot(patrolHeadLocked ? patrolHeadPitch : pitch);
        if (dummy != null) {
            dummy.setYBodyRot(yaw);
            dummy.setYHeadRot(yaw);
            dummy.setXHeadRot(pitch);
        }
    }

    @Override
    public void setPatrolHeadLocked(boolean locked) {
        patrolHeadLocked = locked;
        active.setLockPitch(false);
    }

    @Override
    public void updateLocation(Location location) {
        if (dummy != null) {
            dummy.syncLocation(location);
        }
    }

    @Override
    public boolean isValid() {
        return !modeled.isDestroyed() && !active.isDestroyed();
    }

    @Override
    public void close() {
        destroy(modeled, active);
    }

    static void destroy(ModeledEntity modeled, ActiveModel active) {
        if (!modeled.isDestroyed()) {
            modeled.removeModel(active.getBlueprint().getName());
            modeled.markRemoved();
            modeled.destroy();
        }
        if (!active.isDestroyed()) {
            active.destroy();
        }
    }

    static final class CarrierDummy extends Dummy<LivingEntity> {
        private final LivingEntity carrier;

        CarrierDummy(LivingEntity carrier) {
            super(carrier);
            this.carrier = carrier;
            syncLocation(carrier.getLocation());
        }

        @Override
        public boolean isAlive() {
            return carrier.isValid() && !carrier.isDead();
        }

        @Override
        public boolean isRemoved() {
            return !carrier.isValid();
        }

        @Override
        public float getHealth() {
            return (float) carrier.getHealth();
        }

        @Override
        @SuppressWarnings("deprecation")
        public float getMaxHealth() {
            return (float) carrier.getMaxHealth();
        }

        @Override
        public boolean hurt(HumanEntity source, Object projectile, float damage) {
            return hurtCarrier(source, damage);
        }

        @Override
        public boolean hurt(
                HitboxEntity hitbox,
                HumanEntity source,
                Object projectile,
                float damage) {
            return hurtCarrier(source, damage);
        }

        private boolean hurtCarrier(HumanEntity source, float damage) {
            if (!isAlive()) {
                return false;
            }
            carrier.damage(damage, source);
            return true;
        }
    }
}
