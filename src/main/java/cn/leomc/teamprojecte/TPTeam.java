package cn.leomc.teamprojecte;

import com.google.common.collect.Lists;
import moze_intel.projecte.api.ItemInfo;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class TPTeam {
    private final UUID teamUUID;
    private UUID owner;
    private final List<UUID> members;

    private KnowledgeData knowledge;
    private EMCData emc;

    public TPTeam(UUID teamUUID, UUID owner) {
        this.teamUUID = teamUUID;
        this.owner = owner;
        this.members = new ArrayList<>();
        this.knowledge = new KnowledgeData.Sharing();
        this.emc = new EMCData.Sharing();
    }

    public TPTeam(UUID owner) {
        this(UUID.randomUUID(), owner);
    }

    public TPTeam(CompoundTag tag, String version) {
        this.teamUUID = tag.getUUID("uuid");
        this.owner = tag.getUUID("owner");
        this.members = new ArrayList<>();
        this.members.addAll(tag.getList("members", Tag.TAG_COMPOUND).stream().map(t -> ((CompoundTag) t).getUUID("uuid")).toList());
        switch (version) {
            case "" -> {
                this.knowledge = new KnowledgeData.Sharing();
                tag.getList("knowledge", Tag.TAG_COMPOUND).stream().map(t -> ItemInfo.read(((CompoundTag) t))).filter(Objects::nonNull)
                        .forEach(info -> this.knowledge.addKnowledge(info, Util.NIL_UUID));
                this.emc = new EMCData.Sharing();
                this.emc.setEMC(new BigInteger(tag.getString("emc")), Util.NIL_UUID);
            }
            case "1" -> {
                this.knowledge = KnowledgeData.of(tag.getCompound("knowledge"));
                this.emc = EMCData.of(tag.getCompound("emc"));
            }
        }
    }


    public UUID getUUID() {
        return teamUUID;
    }

    public UUID getOwner() {
        return owner;
    }

    public void addMemberWithKnowledge(TPTeam originalTeam, Player player) {
        markDirty();
        UUID playerUUID = TeamProjectE.getPlayerUUID(player);
        addMember(playerUUID);

        if (originalTeam.getOwner().equals(playerUUID)) {
            setEmc(getEmc(playerUUID).add(originalTeam.getEmc(playerUUID)), playerUUID);
            originalTeam.setEmc(BigInteger.ZERO, playerUUID);
            if (originalTeam.hasFullKnowledge(playerUUID)) {
                setFullKnowledge(true, playerUUID);
                originalTeam.setFullKnowledge(false, playerUUID);
            }
            originalTeam.getKnowledge(playerUUID).forEach(k -> knowledge.addKnowledge(k, playerUUID));
            originalTeam.clearKnowledge(playerUUID);
        } else {
            if (!originalTeam.isSharingEMC()) {
                setEmc(originalTeam.getEmc(playerUUID), playerUUID);
                originalTeam.setEmc(BigInteger.ZERO, playerUUID);
            }
            if (!originalTeam.isSharingKnowledge()) {
                if (originalTeam.hasFullKnowledge(playerUUID)) {
                    setFullKnowledge(true, playerUUID);
                    originalTeam.setFullKnowledge(false, playerUUID);
                }
                originalTeam.getKnowledge(playerUUID).forEach(k -> knowledge.addKnowledge(k, playerUUID));
                originalTeam.clearKnowledge(playerUUID);
            }
        }
        sync();
    }

    public void addMember(UUID uuid) {
        markDirty();
        TPSavedData.getData().invalidateCache(uuid);
        members.add(uuid);
        sync();
    }

    public void removeMember(UUID uuid) {
        markDirty();
        TPSavedData.getData().invalidateCache(uuid);
        knowledge.removeMember(uuid);
        emc.removeMember(uuid);
        if (owner.equals(uuid)) {
            if (members.isEmpty()) {
                TPSavedData.getData().TEAMS.remove(teamUUID);
                return;
            }
            UUID newOwner = members.get(ThreadLocalRandom.current().nextInt(members.size()));
            owner = newOwner;
            members.remove(newOwner);
        } else
            members.remove(uuid);
        sync(uuid);
    }

    public void transferOwner(UUID newOwner) {
        if (owner.equals(newOwner) || !members.contains(newOwner))
            return;
        members.add(owner);
        owner = newOwner;
        members.remove(newOwner);
    }

    public List<UUID> getMembers() {
        return List.copyOf(members);
    }

    public List<UUID> getAll() {
        return Lists.asList(owner, members.toArray(UUID[]::new));
    }


    public boolean addKnowledge(ItemInfo info, UUID player) {
        markDirty();
        return knowledge.addKnowledge(info, player);
    }

    public void removeKnowledge(ItemInfo info, UUID player) {
        markDirty();
        knowledge.removeKnowledge(info, player);
    }

    public void clearKnowledge(UUID player) {
        markDirty();
        knowledge.clearKnowledge(player);
    }


    public Set<ItemInfo> getKnowledge(UUID player) {
        return knowledge.getKnowledge(player);
    }

    public void setEmc(BigInteger emc, UUID player) {
        markDirty();
        this.emc.setEMC(emc, player);
    }

    public BigInteger getEmc(UUID player) {
        return emc.getEMC(player);
    }

    public void setFullKnowledge(boolean fullKnowledge, UUID player) {
        markDirty();
        knowledge.setFullKnowledge(fullKnowledge, player);
    }

    public boolean hasFullKnowledge(UUID player) {
        return knowledge.hasFullKnowledge(player);
    }

    public boolean isSharingEMC() {
        return emc instanceof EMCData.Sharing;
    }

    public boolean isSharingKnowledge() {
        return knowledge instanceof KnowledgeData.Sharing;
    }


    public void setShareEMC(boolean share) {
        if (isSharingEMC() == share)
            return;
        emc = emc.convert(getOwner());
        markDirty();
        sync();
    }

    public void setShareKnowledge(boolean share) {
        if (isSharingKnowledge() == share)
            return;
        knowledge = knowledge.convert(getOwner());
        markDirty();
        sync();
    }

    public void markDirty() {
        TPSavedData.getData().setDirty();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("uuid", teamUUID);
        tag.putUUID("owner", owner);
        ListTag list = new ListTag();
        for (UUID member : members) {
            CompoundTag t = new CompoundTag();
            t.putUUID("uuid", member);
            list.add(t);
        }
        tag.put("members", list);

        tag.put("knowledge", knowledge.save());
        tag.put("emc", emc.save());

        return tag;
    }


    public static TPTeam getOrCreateTeam(UUID uuid) {
        TPTeam team = getTeamByMember(uuid);
        if (team == null)
            team = createTeam(uuid);
        return team;
    }

    public static TPTeam createTeam(UUID uuid) {
        TPTeam team = new TPTeam(uuid);
        TPSavedData.getData().TEAMS.put(team.getUUID(), team);
        TPSavedData.getData().setDirty();
        return team;
    }

    public static TPTeam getTeam(UUID uuid) {
        return TPSavedData.getData().TEAMS.get(uuid);
    }

    public static boolean isInTeam(UUID uuid) {
        return getTeamByMember(uuid) != null;
    }

    public static TPTeam getTeamByMember(UUID uuid) {
        UUID teamUUID = TPSavedData.getData().PLAYER_TEAM_CACHE.get(uuid);

        if (teamUUID == null)
            for (Map.Entry<UUID, TPTeam> entry : TPSavedData.getData().TEAMS.entrySet()) {
                if (entry.getValue().getAll().contains(uuid)) {
                    teamUUID = entry.getKey();
                    TPSavedData.getData().PLAYER_TEAM_CACHE.put(uuid, teamUUID);
                    break;
                }
            }

        if (teamUUID != null)
            return TPSavedData.getData().TEAMS.get(teamUUID);
        return null;
    }

    public void sync() {
        TeamProjectE.getAllOnline(getAll()).forEach(TeamProjectE::sync);
    }

    public void sync(UUID uuid) {
        TeamProjectE.getAllOnline(List.of(uuid)).forEach(TeamProjectE::sync);
    }

}
