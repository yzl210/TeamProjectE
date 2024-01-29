package cn.leomc.teamprojecte.mixin;

import cn.leomc.teamprojecte.TeamKnowledgeProvider;
import moze_intel.projecte.impl.capability.KnowledgeImpl;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.fml.common.thread.SidedThreadGroups;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.lang.reflect.Field;

@Mixin(KnowledgeImpl.Provider.class)
public class KnowledgeEventsProviderMixin {

    @Unique
    private static Class<?> DEFAULT_IMPL_CLASS;
    @Unique
    private static Field PLAYER_FIELD;

    static {
        try {
            DEFAULT_IMPL_CLASS = Class.forName("moze_intel.projecte.impl.capability.KnowledgeImpl$DefaultImpl");
            PLAYER_FIELD = Class.forName("moze_intel.projecte.impl.capability.KnowledgeImpl$DefaultImpl").getDeclaredField("player");
            PLAYER_FIELD.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @ModifyArg(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lmoze_intel/projecte/capability/managing/SerializableCapabilityResolver;<init>(Lnet/minecraftforge/common/util/INBTSerializable;)V"),
            index = 0,
            remap = false)
    private static INBTSerializable<CompoundNBT> onInit(INBTSerializable<CompoundNBT> internal) throws IllegalAccessException {
        if (Thread.currentThread().getThreadGroup() == SidedThreadGroups.SERVER && DEFAULT_IMPL_CLASS.isInstance(internal))
            return new TeamKnowledgeProvider((ServerPlayerEntity) PLAYER_FIELD.get(internal));
        return internal;
    }
}
