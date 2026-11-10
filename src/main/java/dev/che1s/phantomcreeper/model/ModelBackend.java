package dev.che1s.phantomcreeper.model;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

/**
 * 生物模型引擎的统一后端接口。
 */
public interface ModelBackend {

    /**
     * 后端名称，用于指令状态和日志。
     */
    String name();

    /**
     * 模型蓝图是否已经加载。
     */
    boolean isLoaded();

    /**
     * 将模型附加到隐藏的原版生物载体。
     */
    ModelAttachment attach(LivingEntity entity);

    /**
     * 在指定位置播放一次独立死亡模型动画。
     */
    void playDeathEffect(Location location);

    /**
     * 注册模型导入完成回调。
     */
    void register(Runnable callback);

    /**
     * 释放后端注册的事件与临时模型。
     */
    void close();
}
