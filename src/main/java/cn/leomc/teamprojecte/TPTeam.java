package cn.leomc.teamprojecte;

import com.google.common.collect.Lists;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.PECapabilities;
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

    private final Set<ItemInfo> knowledge;
    private BigInteger emc;
    private boolean fullKnowledge;

    public TPTeam(UUID teamUUID, UUID owner){
        this.teamUUID = teamUUID;
        this.owner = owner;
        this.members = new ArrayList<>();
        this.knowledge = new HashSet<>();
        this.emc = BigInteger.ZERO;
        this.fullKnowledge = false;
    }

    public TPTeam(UUID owner){
        this(UUID.randomUUID(), owner);
    }

    public TPTeam(CompoundTag tag){
        this(tag.getUUID("uuid"), tag.getUUID("owner"));
        this.members.addAll(tag.getList("members", Tag.TAG_COMPOUND).stream().map(t -> ((CompoundTag) t).getUUID("uuid")).toList());

        this.knowledge.addAll(tag.getList("knowledge", Tag.TAG_COMPOUND).stream().map(t -> ItemInfo.read(((CompoundTag)t))).filter(Objects::nonNull).toList());
        this.emc = new BigInteger(tag.getString("emc"));
        this.fullKnowledge = tag.getBoolean("fullKnowledge");
    }

    public UUID getUUID() {
        return teamUUID;
    }

    public UUID getOwner(){
        return owner;
    }

    public void addMemberWithKnowledge(TPTeam originalTeam, Player player){
        markDirty();
        addMember(TeamProjectE.getPlayerUUID(player));
        if(originalTeam.getOwner().equals(TeamProjectE.getPlayerUUID(player))){
            setEmc(getEmc().add(originalTeam.getEmc()));
            originalTeam.setEmc(BigInteger.ZERO);
            if(originalTeam.hasFullKnowledge()) {
                setFullKnowledge(true);
                originalTeam.setFullKnowledge(false);
            }
            knowledge.addAll(originalTeam.getKnowledge());
            originalTeam.clearKnowledge();
        }
    }

    public void addMember(UUID uuid){
        markDirty();
        TPSavedData.getData().invalidateCache(uuid);
        members.add(uuid);
    }

    public void removeMember(UUID uuid){
        markDirty();
        TPSavedData.getData().invalidateCache(uuid);
        if(owner.equals(uuid)){
            if(members.isEmpty()){
                TPSavedData.getData().TEAMS.remove(teamUUID);
                return;
            }
            UUID newOwner = members.get(ThreadLocalRandom.current().nextInt(members.size()));
            owner = newOwner;
            members.remove(newOwner);
        }
        else
            members.remove(uuid);
    }

    public void transferOwner(UUID newOwner){
        if(owner.equals(newOwner) || !members.contains(newOwner))
            return;
        members.add(owner);
        owner = newOwner;
    }

    public List<UUID> getMembers(){
        return List.copyOf(members);
    }

    public List<UUID> getAll(){
        return Lists.asList(owner, members.toArray(UUID[]::new));
    }


    public boolean addKnowledge(ItemInfo info){
        markDirty();
        return knowledge.add(info);
    }

    public void removeKnowledge(ItemInfo info){
        markDirty();
        knowledge.remove(info);
    }

    public void clearKnowledge(){
        markDirty();
        knowledge.clear();
    }


    public Set<ItemInfo> getKnowledge() {
        return Set.copyOf(knowledge);
    }

    public void setEmc(BigInteger emc) {
        markDirty();
        this.emc = emc;
    }

    public BigInteger getEmc() {
        return emc;
    }

    public void setFullKnowledge(boolean fullKnowledge) {
        markDirty();
        this.fullKnowledge = fullKnowledge;
    }

    public boolean hasFullKnowledge() {
        return fullKnowledge;
    }

    public void markDirty(){
        TPSavedData.getData().setDirty();
    }

    public CompoundTag save(){
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

        ListTag itemInfos = new ListTag();
        for (ItemInfo info : knowledge)
            itemInfos.add(info.write(new CompoundTag()));
        tag.put("knowledge", itemInfos);
        tag.putString("emc", emc.toString());
        tag.putBoolean("fullKnowledge", fullKnowledge);

        return tag;
    }


    public static TPTeam getOrCreateTeam(UUID uuid){
        TPTeam team = getTeamByMember(uuid);
        if(team == null)
            team = createTeam(uuid);
        return team;
    }

    public static TPTeam createTeam(UUID uuid){
        TPTeam team = new TPTeam(uuid);
        TPSavedData.getData().TEAMS.put(team.getUUID(), team);
        TPSavedData.getData().setDirty();
        return team;
    }

    public static TPTeam getTeam(UUID uuid) {
        return TPSavedData.getData().TEAMS.get(uuid);
    }

    public static boolean isInTeam(UUID uuid){
        return getTeamByMember(uuid) != null;
    }

    public static TPTeam getTeamByMember(UUID uuid){
        UUID teamUUID = TPSavedData.getData().PLAYER_TEAM_CACHE.get(uuid);

        if(teamUUID == null)
            for (Map.Entry<UUID, TPTeam> entry : TPSavedData.getData().TEAMS.entrySet()) {
                if(entry.getValue().getAll().contains(uuid)) {
                    teamUUID = entry.getKey();
                    TPSavedData.getData().PLAYER_TEAM_CACHE.put(uuid, teamUUID);
                    break;
                }
            }

        if(teamUUID != null)
            return TPSavedData.getData().TEAMS.get(teamUUID);
        return null;
    }
}
