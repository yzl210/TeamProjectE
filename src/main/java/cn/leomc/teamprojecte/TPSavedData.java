package cn.leomc.teamprojecte;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TPSavedData extends WorldSavedData {

    private static TPSavedData DATA;

    static TPSavedData getData() {
        if (DATA == null && ServerLifecycleHooks.getCurrentServer() != null)
            DATA = ServerLifecycleHooks.getCurrentServer().overworld().getDataStorage()
                    .computeIfAbsent(TPSavedData::new, "teamprojecte");
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
        super("teamprojecte");
    }

    @Override
    public void load(CompoundNBT tag) {
        TeamProjectE.LOGGER.debug(tag.toString());
        String version = tag.getString("version");
        for (INBT t : tag.getList("teams", Constants.NBT.TAG_COMPOUND)) {
            CompoundNBT team = (CompoundNBT) t;
            teams.put(team.getUUID("uuid"), new TPTeam(team.getCompound("team"), version));
        }
    }

    @Override
    public @Nonnull CompoundNBT save(CompoundNBT tag) {
        tag.putString("version", "1");

        ListNBT teams = new ListNBT();
        this.teams.forEach((uuid, team) -> {
            CompoundNBT t = new CompoundNBT();
            t.putUUID("uuid", uuid);
            t.put("team", team.save());
            teams.add(t);
        });
        tag.put("teams", teams);
        return tag;
    }
}
