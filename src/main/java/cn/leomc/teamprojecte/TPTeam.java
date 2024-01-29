package cn.leomc.teamprojecte;

import com.google.common.collect.Lists;
import moze_intel.projecte.api.ItemInfo;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.Util;
import net.minecraftforge.common.util.Constants;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

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

    public TPTeam(CompoundNBT tag, String version) {
        this.teamUUID = tag.getUUID("uuid");
        this.owner = tag.getUUID("owner");
        this.members = new ArrayList<>();
        this.members.addAll(tag.getList("members", Constants.NBT.TAG_COMPOUND).stream().map(t -> ((CompoundNBT) t).getUUID("uuid")).collect(Collectors.toList()));
        switch (version) {
            case "":
                this.knowledge = new KnowledgeData.Sharing();
                tag.getList("knowledge", Constants.NBT.TAG_COMPOUND).stream().map(t -> ItemInfo.read(((CompoundNBT) t))).filter(Objects::nonNull)
                        .forEach(info -> this.knowledge.addKnowledge(info, Util.NIL_UUID));
                this.emc = new EMCData.Sharing();
                this.emc.setEMC(new BigInteger(tag.getString("emc")), Util.NIL_UUID);
                break;
            case "1":
                this.knowledge = KnowledgeData.of(tag.getCompound("knowledge"));
                this.emc = EMCData.of(tag.getCompound("emc"));
                break;
        }
    }


    public UUID getUUID() {
        return teamUUID;
    }

    public UUID getOwner() {
        return owner;
    }

    public void addMemberWithKnowledge(TPTeam originalTeam, PlayerEntity player) {
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
            originalTeam.getKnowledge(playerUUID).forEach(k -> addKnowledge(k, playerUUID));
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
                originalTeam.getKnowledge(playerUUID).forEach(k -> addKnowledge(k, playerUUID));
                originalTeam.clearKnowledge(playerUUID);
            }
        }
        originalTeam.removeMember(playerUUID);
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
                TPSavedData.getData().teams.remove(teamUUID);
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
        return Collections.unmodifiableList(members);
    }

    public List<UUID> getAll() {
        return Lists.asList(owner, members.toArray(new UUID[0]));
    }


    public boolean addKnowledge(ItemInfo info, UUID player) {
        markDirty();
        return knowledge.addKnowledge(info, player);
    }

    public boolean removeKnowledge(ItemInfo info, UUID player) {
        markDirty();
        return knowledge.removeKnowledge(info, player);
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

    public CompoundNBT save() {
        CompoundNBT tag = new CompoundNBT();
        tag.putUUID("uuid", teamUUID);
        tag.putUUID("owner", owner);
        ListNBT list = new ListNBT();
        for (UUID member : members) {
            CompoundNBT t = new CompoundNBT();
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
        TPSavedData.getData().teams.put(team.getUUID(), team);
        TPSavedData.getData().setDirty();
        return team;
    }

    public static TPTeam getTeam(UUID uuid) {
        return TPSavedData.getData().teams.get(uuid);
    }

    public static boolean isInTeam(UUID uuid) {
        return getTeamByMember(uuid) != null;
    }

    public static TPTeam getTeamByMember(UUID uuid) {
        UUID teamUUID = TPSavedData.getData().playerTeamCache.get(uuid);

        if (teamUUID == null)
            for (Map.Entry<UUID, TPTeam> entry : TPSavedData.getData().teams.entrySet()) {
                if (entry.getValue().getAll().contains(uuid)) {
                    teamUUID = entry.getKey();
                    TPSavedData.getData().playerTeamCache.put(uuid, teamUUID);
                    break;
                }
            }

        if (teamUUID != null)
            return TPSavedData.getData().teams.get(teamUUID);
        return null;
    }

    public void sync() {
        TeamProjectE.getAllOnline(getAll()).forEach(TeamProjectE::sync);
    }

    public void sync(UUID uuid) {
        TeamProjectE.getAllOnline(Lists.newArrayList(uuid)).forEach(TeamProjectE::sync);
    }

}
