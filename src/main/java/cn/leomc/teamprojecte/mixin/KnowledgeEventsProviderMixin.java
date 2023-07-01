package cn.leomc.teamprojecte.mixin;

import cn.leomc.teamprojecte.TeamKnowledgeProvider;
import moze_intel.projecte.impl.capability.KnowledgeImpl;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.fml.util.thread.SidedThreadGroups;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.lang.reflect.Field;

@Mixin(KnowledgeImpl.Provider.class)
public class KnowledgeEventsProviderMixin {

    private static Field PLAYER_FIELD;

    static {
        try {
            PLAYER_FIELD = Class.forName("moze_intel.projecte.impl.capability.KnowledgeImpl$DefaultImpl").getDeclaredField("player");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @ModifyArg(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lmoze_intel/projecte/capability/managing/SerializableCapabilityResolver;<init>(Lnet/minecraftforge/common/util/INBTSerializable;)V"),
            index = 0,
            remap = false)
    private static INBTSerializable<CompoundTag> onInit(INBTSerializable<CompoundTag> internal) throws IllegalAccessException {
        if (Thread.currentThread().getThreadGroup() == SidedThreadGroups.SERVER && internal instanceof KnowledgeImpl.DefaultImpl impl)
            return new TeamKnowledgeProvider((ServerPlayer) PLAYER_FIELD.get(impl));
        return internal;
    }
}
