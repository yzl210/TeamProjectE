package cn.leomc.teamprojecte;

import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public interface EMCData {
    BigInteger getEMC(UUID player);

    void setEMC(BigInteger emc, UUID player);

    void removeMember(UUID player);

    EMCData convert(UUID owner);

    void load(CompoundTag tag);

    CompoundTag save();

    static EMCData of(CompoundTag tag) {
        EMCData data = null;
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


    class Sharing implements EMCData {
        private BigInteger emc = BigInteger.ZERO;

        @Override
        public BigInteger getEMC(UUID player) {
            return emc;
        }

        @Override
        public void setEMC(BigInteger emc, UUID player) {
            this.emc = emc;
        }

        @Override
        public void removeMember(UUID player) {
        }

        @Override
        public EMCData convert(UUID owner) {
            EMCData data = new NotSharing();
            data.setEMC(emc, owner);
            return data;
        }

        @Override
        public void load(CompoundTag tag) {
            emc = new BigInteger(tag.getString("emc"));
        }

        @Override
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("emc", emc.toString());
            tag.putString("type", getType());
            return tag;
        }

        public static String getType() {
            return "sharing";
        }
    }

    class NotSharing implements EMCData {
        private final Map<UUID, BigInteger> emc = new HashMap<>();

        @Override
        public BigInteger getEMC(UUID player) {
            return emc.getOrDefault(player, BigInteger.ZERO);
        }

        @Override
        public void setEMC(BigInteger emc, UUID player) {
            this.emc.put(player, emc);
        }

        @Override
        public void removeMember(UUID player) {
            emc.remove(player);
        }

        @Override
        public EMCData convert(UUID owner) {
            EMCData data = new Sharing();
            data.setEMC(emc.values().stream().reduce(BigInteger.ZERO, BigInteger::add), Util.NIL_UUID);
            return data;
        }

        @Override
        public void load(CompoundTag tag) {
            emc.clear();
            tag.getList("emc", Tag.TAG_COMPOUND).forEach((t) -> {
                CompoundTag ct = (CompoundTag) t;
                emc.put(ct.getUUID("player"), new BigInteger(ct.getString("emc")));
            });
        }

        @Override
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            ListTag list = new ListTag();
            emc.forEach((uuid, emc) -> {
                CompoundTag t = new CompoundTag();
                t.putUUID("player", uuid);
                t.putString("emc", emc.toString());
                list.add(t);
            });
            tag.put("emc", list);
            tag.putString("type", getType());
            return tag;
        }

        public static String getType() {
            return "not_sharing";
        }
    }
}
