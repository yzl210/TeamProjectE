package cn.leomc.teamprojecte;

import com.mojang.logging.LogUtils;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Mod("teamprojecte")
public class TeamProjectE {

    public static final Logger LOGGER = LogUtils.getLogger();

    public TeamProjectE() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommand(RegisterCommandsEvent event) {
        TPCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        TPCommand.INVITATIONS.clear();
        TPSavedData.onServerStopped();
    }

    @SubscribeEvent
    public void onPlayerJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().dimension() == Level.OVERWORLD && event.getEntity() instanceof ServerPlayer player)
            sync(player);
    }

    public static List<ServerPlayer> getAllOnline(List<UUID> uuids) {
        return uuids.stream()
                .map(uuid -> ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(uuid))
                .filter(Objects::nonNull)
                .toList();
    }

    public static void sync(ServerPlayer player) {
        player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).ifPresent(provider -> provider.sync(player));
    }

    public static List<ServerPlayer> getOnlineTeamMembers(UUID uuid) {
        return getOnlineTeamMembers(uuid, true);
    }

    public static List<ServerPlayer> getOnlineTeamMembers(UUID uuid, boolean includeOwner) {
        TPTeam team = TPTeam.getOrCreateTeam(uuid);
        return TeamProjectE.getAllOnline(includeOwner ? team.getAll() : team.getMembers());
    }

    public static UUID getPlayerUUID(Player player) {
        UUID uuid = player.getGameProfile().getId();
        if (uuid == null)
            uuid = player.getUUID();
        return uuid;
    }

}