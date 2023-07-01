package cn.leomc.teamprojecte;

import moze_intel.projecte.api.ItemInfo;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.*;
import java.util.stream.Collectors;

public interface KnowledgeData {

    boolean addKnowledge(ItemInfo info, UUID player);

    void removeKnowledge(ItemInfo info, UUID player);

    void clearKnowledge(UUID player);

    Set<ItemInfo> getKnowledge(UUID player);

    void setFullKnowledge(boolean fullKnowledge, UUID player);

    boolean hasFullKnowledge(UUID player);

    void removeMember(UUID player);

    KnowledgeData convert(UUID owner);

    void load(CompoundTag tag);

    CompoundTag save();

    static KnowledgeData of(CompoundTag tag) {
        KnowledgeData data = null;
        String type = tag.getString("type");
        if (Sharing.getType().equals(type))
            data = new Sharing();
        if (NotSharing.getType().equals(type))
            data = new NotSharing();
        if (data == null)
            return new Sharing();
        data.load(tag);
        return data;
    }


    class Sharing implements KnowledgeData {
        private final Set<ItemInfo> knowledge = new HashSet<>();
        private boolean fullKnowledge = false;

        @Override
        public boolean addKnowledge(ItemInfo info, UUID player) {
            return knowledge.add(info);
        }

        @Override
        public void removeKnowledge(ItemInfo info, UUID player) {
            knowledge.remove(info);
        }

        @Override
        public void clearKnowledge(UUID player) {
            knowledge.clear();
        }

        @Override
        public Set<ItemInfo> getKnowledge(UUID player) {
            return Set.copyOf(knowledge);
        }

        @Override
        public void setFullKnowledge(boolean fullKnowledge, UUID player) {
            this.fullKnowledge = fullKnowledge;
        }

        @Override
        public boolean hasFullKnowledge(UUID player) {
            return fullKnowledge;
        }

        @Override
        public void removeMember(UUID player) {
        }

        @Override
        public KnowledgeData convert(UUID owner) {
            KnowledgeData data = new NotSharing();
            knowledge.forEach(info -> data.addKnowledge(info, owner));
            data.setFullKnowledge(fullKnowledge, owner);
            return data;
        }

        @Override
        public void load(CompoundTag tag) {
            knowledge.clear();
            knowledge.addAll(tag.getList("knowledge", Tag.TAG_COMPOUND).stream().map(t -> ItemInfo.read(((CompoundTag) t))).filter(Objects::nonNull).toList());
            fullKnowledge = tag.getBoolean("fullKnowledge");
        }

        @Override
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            ListTag itemInfos = new ListTag();
            for (ItemInfo info : knowledge)
                itemInfos.add(info.write(new CompoundTag()));
            tag.put("knowledge", itemInfos);
            tag.putBoolean("fullKnowledge", fullKnowledge);
            tag.putString("type", getType());
            return tag;
        }

        public static String getType() {
            return "sharing";
        }
    }

    class NotSharing implements KnowledgeData {

        private final Map<UUID, Set<ItemInfo>> knowledge = new HashMap<>();
        private final Set<UUID> fullKnowledge = new HashSet<>();


        @Override
        public boolean addKnowledge(ItemInfo info, UUID player) {
            return knowledge.computeIfAbsent(player, k -> new HashSet<>()).add(info);
        }

        @Override
        public void removeKnowledge(ItemInfo info, UUID player) {
            if (knowledge.containsKey(player))
                knowledge.get(player).remove(info);
        }

        @Override
        public void clearKnowledge(UUID player) {
            knowledge.remove(player);
        }

        @Override
        public Set<ItemInfo> getKnowledge(UUID player) {
            return Set.copyOf(knowledge.getOrDefault(player, Collections.emptySet()));
        }

        @Override
        public void setFullKnowledge(boolean fullKnowledge, UUID player) {
            if (fullKnowledge)
                this.fullKnowledge.add(player);
            else
                this.fullKnowledge.remove(player);
        }

        @Override
        public boolean hasFullKnowledge(UUID player) {
            return fullKnowledge.contains(player);
        }

        @Override
        public void removeMember(UUID player) {
            knowledge.remove(player);
            fullKnowledge.remove(player);
        }

        @Override
        public KnowledgeData convert(UUID owner) {
            KnowledgeData data = new Sharing();
            knowledge.values().stream().flatMap(Collection::stream).forEach(info -> data.addKnowledge(info, Util.NIL_UUID));
            data.setFullKnowledge(!fullKnowledge.isEmpty(), Util.NIL_UUID);
            return data;
        }

        @Override
        public void load(CompoundTag tag) {
            knowledge.clear();
            fullKnowledge.clear();
            tag.getList("knowledge", Tag.TAG_COMPOUND).forEach(t -> {
                CompoundTag ct = (CompoundTag) t;
                knowledge.put(ct.getUUID("player"),
                        ct.getList("knowledge", Tag.TAG_COMPOUND).stream().map(i -> ItemInfo.read(((CompoundTag) i))).filter(Objects::nonNull).collect(Collectors.toSet()));
            });

            tag.getList("fullKnowledge", Tag.TAG_INT_ARRAY).forEach(t -> fullKnowledge.add(NbtUtils.loadUUID(t)));
        }

        @Override
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();

            ListTag k = new ListTag();
            knowledge.forEach((uuid, knowledge) -> {
                CompoundTag t = new CompoundTag();
                t.putUUID("player", uuid);
                ListTag list = new ListTag();
                knowledge.forEach(info -> list.add(info.write(new CompoundTag())));
                t.put("knowledge", list);
                k.add(t);
            });
            tag.put("knowledge", k);

            ListTag fk = new ListTag();
            fullKnowledge.forEach(uuid -> fk.add(NbtUtils.createUUID(uuid)));
            tag.put("fullKnowledge", fk);

            tag.putString("type", getType());
            return tag;
        }

        public static String getType() {
            return "not_sharing";
        }
    }
}
