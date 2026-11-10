package dev.che1s.phantomcreeper.model;

import org.bukkit.Location;

/**
 * 表示一只幻翼苦力怕当前使用的模型实例。
 *
 * <p>该接口隔离 BetterModel 与 ModelEngine，控制器只负责调用统一的
 * 动画、转头、受击染色和命中箱接口。</p>
 */
public interface ModelAttachment {

    /**
     * 返回当前模型引擎提供的命中箱数量。
     */
    int hitboxCount();

    /**
     * 播放一次模型受伤染色。
     */
    void damageTint();

    /**
     * 将模型头部转向指定绝对偏航角和俯仰角。
     */
    void setHeadRotation(float yaw, float pitch);

    /**
     * 锁定或解除盘旋状态下的头部俯仰角。
     */
    void setPatrolHeadLocked(boolean locked);

    /**
     * 同步模型参考位置。使用真实载体时通常无需额外操作。
     */
    void updateLocation(Location location);

    /**
     * 模型实例是否仍然有效。
     */
    boolean isValid();

    /**
     * 关闭模型实例并释放引擎侧资源。
     */
    void close();
}
