package com.github.tacowasa059.katamariio.common.networks;

import com.github.tacowasa059.katamariio.common.accessors.ICustomPlayerData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Sends attached block data from server to client in batches.
 * First batch (batchIndex==0) clears existing data, subsequent batches append.
 */
public class S2CBlockListPacket {
    private final int playerEntityId;
    private final int batchIndex;
    private final int totalBatches;
    private final List<Block> blocks;
    private final List<Vec3> positions;
    private final List<Quaternionf> quaternions;

    public S2CBlockListPacket(int playerEntityId, int batchIndex, int totalBatches,
                              List<Block> blocks, List<Vec3> positions, List<Quaternionf> quaternions) {
        this.playerEntityId = playerEntityId;
        this.batchIndex = batchIndex;
        this.totalBatches = totalBatches;
        this.blocks = blocks;
        this.positions = positions;
        this.quaternions = quaternions;
    }

    public static void encode(S2CBlockListPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.playerEntityId);
        buf.writeVarInt(packet.batchIndex);
        buf.writeVarInt(packet.totalBatches);
        buf.writeVarInt(packet.blocks.size());
        for (int i = 0; i < packet.blocks.size(); i++) {
            ResourceLocation key = ForgeRegistries.BLOCKS.getKey(packet.blocks.get(i));
            buf.writeUtf(key != null ? key.toString() : "minecraft:air");
            Vec3 pos = packet.positions.get(i);
            buf.writeDouble(pos.x);
            buf.writeDouble(pos.y);
            buf.writeDouble(pos.z);
            Quaternionf q = packet.quaternions.get(i);
            buf.writeFloat(q.x);
            buf.writeFloat(q.y);
            buf.writeFloat(q.z);
            buf.writeFloat(q.w);
        }
    }

    public static S2CBlockListPacket decode(FriendlyByteBuf buf) {
        int playerEntityId = buf.readVarInt();
        int batchIndex = buf.readVarInt();
        int totalBatches = buf.readVarInt();
        int size = buf.readVarInt();
        List<Block> blocks = new ArrayList<>(size);
        List<Vec3> positions = new ArrayList<>(size);
        List<Quaternionf> quaternions = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String blockId = buf.readUtf();
            ResourceLocation key = ResourceLocation.tryParse(blockId);
            Block block = (key != null && ForgeRegistries.BLOCKS.containsKey(key))
                    ? ForgeRegistries.BLOCKS.getValue(key)
                    : net.minecraft.world.level.block.Blocks.AIR;
            blocks.add(block);
            positions.add(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
            quaternions.add(new Quaternionf(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
        }
        return new S2CBlockListPacket(playerEntityId, batchIndex, totalBatches, blocks, positions, quaternions);
    }

    public static void handle(S2CBlockListPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> applyOnClient(packet));
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void applyOnClient(S2CBlockListPacket packet) {
        if (Minecraft.getInstance().level == null) return;
        Entity entity = Minecraft.getInstance().level.getEntity(packet.playerEntityId);
        if (!(entity instanceof Player player)) return;
        ICustomPlayerData data = (ICustomPlayerData) player;

        if (packet.batchIndex == 0 && packet.totalBatches > 0) {
            data.katamariIO$clearAttachedBlocks();
        }

        for (int i = 0; i < packet.blocks.size(); i++) {
            data.katamariIO$addBlock(packet.blocks.get(i), packet.quaternions.get(i), packet.positions.get(i));
        }
    }
}
