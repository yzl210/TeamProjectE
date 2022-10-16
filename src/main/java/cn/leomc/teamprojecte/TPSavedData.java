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

    public static TPSavedData getData(){
        if(DATA == null && ServerLifecycleHooks.getCurrentServer() != null)
            DATA = ServerLifecycleHooks.getCurrentServer().overworld().getDataStorage()
                    .computeIfAbsent(TPSavedData::new, TPSavedData::create, "teamprojecte");
        return DATA;
    }

    public static void onServerStopped(){
        DATA = null;
    }

    public Map<UUID, TPTeam> TEAMS = new HashMap<>();
    public final Map<UUID, UUID> PLAYER_TEAM_CACHE = new HashMap<>();

    public void invalidateCache(UUID uuid){
        PLAYER_TEAM_CACHE.remove(uuid);
    }

    public TPSavedData() {
    }

    public TPSavedData(CompoundTag tag) {
        TeamProjectE.LOGGER.info(tag.toString());
        for (Tag t : tag.getList("teams", Tag.TAG_COMPOUND)) {
            CompoundTag team = (CompoundTag) t;
            TEAMS.put(team.getUUID("uuid"), new TPTeam(team.getCompound("team")));
        }
    }

    public static TPSavedData create(){
        return new TPSavedData();
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag tag) {
        ListTag teams = new ListTag();
        TEAMS.forEach((uuid, team) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("uuid", uuid);
            t.put("team", team.save());
            teams.add(t);
        });
        tag.put("teams", teams);
        return tag;
    }
}
