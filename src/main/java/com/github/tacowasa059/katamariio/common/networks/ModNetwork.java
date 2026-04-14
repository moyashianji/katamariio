package com.github.tacowasa059.katamariio.common.networks;

import com.github.tacowasa059.katamariio.KatamariIO;
import com.github.tacowasa059.katamariio.common.accessors.ICustomPlayerData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.joml.Quaternionf;

import java.util.List;
import java.util.Optional;

public class ModNetwork {
    public static final String PROTOCOL_VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(KatamariIO.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static final int BLOCK_LIST_BATCH_SIZE = 50;
    private static int id = 0;

    public static void register() {
        CHANNEL.registerMessage(id++, RemoveBlockPacket.class,
                RemoveBlockPacket::toBytes,
                RemoveBlockPacket::fromBytes,
                RemoveBlockPacket::handle);
        CHANNEL.registerMessage(id++, S2CPlayerPacket.class, S2CPlayerPacket::encode,
                S2CPlayerPacket::decode, S2CPlayerPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, S2CBlockListPacket.class, S2CBlockListPacket::encode,
                S2CBlockListPacket::decode, S2CBlockListPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendInitialData(ServerPlayer player,
                                       float collisionSize, float renderSize, boolean flag, float restitution,
                                       Quaternionf quaternion, Vec3 currentPos) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CPlayerPacket(collisionSize, renderSize, flag, restitution, quaternion, currentPos));
    }

    /**
     * Send attached block data to a client in batches to avoid login timeout.
     */
    public static void sendBlockListToPlayer(ServerPlayer target, Player sourcePlayer) {
        ICustomPlayerData data = (ICustomPlayerData) sourcePlayer;
        List<Block> blocks = data.katamariIO$getSphericalPlayerBlocks();
        List<Vec3> positions = data.katamariIO$getSphericalPlayerPositions();
        List<Quaternionf> quaternions = data.katamariIO$getSphericalPlayerQuaternions();
        int size = Math.min(blocks.size(), Math.min(positions.size(), quaternions.size()));

        if (size == 0) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> target),
                    new S2CBlockListPacket(sourcePlayer.getId(), 0, 1,
                            List.of(), List.of(), List.of()));
            return;
        }

        int totalBatches = (size + BLOCK_LIST_BATCH_SIZE - 1) / BLOCK_LIST_BATCH_SIZE;
        for (int batch = 0; batch < totalBatches; batch++) {
            int from = batch * BLOCK_LIST_BATCH_SIZE;
            int to = Math.min(from + BLOCK_LIST_BATCH_SIZE, size);
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> target),
                    new S2CBlockListPacket(
                            sourcePlayer.getId(), batch, totalBatches,
                            blocks.subList(from, to),
                            positions.subList(from, to),
                            quaternions.subList(from, to)));
        }
    }
}

