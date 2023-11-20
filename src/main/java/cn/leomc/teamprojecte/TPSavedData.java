package cn.leomc.teamprojecte;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TPSavedData extends SavedData {

    private static TPSavedData DATA;

    static TPSavedData getData() {
        if (DATA == null && ServerLifecycleHooks.getCurrentServer() != null)
            DATA = ServerLifecycleHooks.getCurrentServer().overworld().getDataStorage()
                    .computeIfAbsent(TPSavedData::new, TPSavedData::new, "teamprojecte");
        return DATA;
    }

    static void onServerStopped() {
        DATA = null;
    }

    final Map<UUID, TPTeam> teams = new HashMap<>();
    final Map<UUID, UUID> playerTeamCache = new HashMap<>();

    void invalidateCache(UUID uuid) {
        playerTeamCache.remove(uuid);
    }

    TPSavedData() {
    }

    TPSavedData(CompoundTag tag) {
        TeamProjectE.LOGGER.debug(tag.toString());
        String version = tag.getString("version");
        for (Tag t : tag.getList("teams", Tag.TAG_COMPOUND)) {
            CompoundTag team = (CompoundTag) t;
            teams.put(team.getUUID("uuid"), new TPTeam(team.getCompound("team"), version));
        }
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag tag) {
        tag.putString("version", "1");

        ListTag teams = new ListTag();
        this.teams.forEach((uuid, team) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("uuid", uuid);
            t.put("team", team.save());
            teams.add(t);
        });
        tag.put("teams", teams);
        return tag;
    }
}
