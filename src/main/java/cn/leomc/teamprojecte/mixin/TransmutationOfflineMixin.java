package cn.leomc.teamprojecte.mixin;

import cn.leomc.teamprojecte.TeamKnowledgeProvider;
import com.google.common.base.Preconditions;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.impl.TransmutationOffline;
import net.minecraftforge.fml.util.thread.SidedThreadGroups;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.UUID;

@Mixin(TransmutationOffline.class)
public class TransmutationOfflineMixin {

    @Shadow(remap = false) @Final private static Map<UUID, IKnowledgeProvider> cachedKnowledgeProviders;

    @Inject(
            method = "cacheOfflineData",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void onCacheOfflinePlayerData(UUID playerUUID, CallbackInfoReturnable<Boolean> cir){
        cir.cancel();
        Preconditions.checkState(Thread.currentThread().getThreadGroup() == SidedThreadGroups.SERVER);
        cachedKnowledgeProviders.put(playerUUID, new TeamKnowledgeProvider(playerUUID));
        cir.setReturnValue(true);
    }

}
