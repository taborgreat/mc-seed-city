package net.tabor.seedcity.client;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
public final class SentinelRenderState extends LivingEntityRenderState {
    public int alertness;
    public float attackTime;
    /** Optional light jog; renderer follows isSprinting, independently of hostility. */
    public boolean jogging;
}
