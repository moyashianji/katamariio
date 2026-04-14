package com.github.tacowasa059.katamariio.common.networks;

import com.github.tacowasa059.katamariio.common.accessors.ICustomPlayerData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.Supplier;

public class RemoveBlockPacket {
    private final BlockPos pos;
    private final Vec3 playerPos;

    public RemoveBlockPacket(BlockPos pos, Vec3 loc) {
        this.pos = pos;
        this.playerPos = loc;
    }

    public static RemoveBlockPacket fromBytes(FriendlyByteBuf buf) {
        return new RemoveBlockPacket(buf.readBlockPos(), new Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble()));
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeDouble(playerPos.x);
        buf.writeDouble(playerPos.y);
        buf.writeDouble(playerPos.z);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer serverPlayer = context.getSender();
            if (serverPlayer != null) {
                Level level = serverPlayer.level();
                Block block = level.getBlockState(pos).getBlock();

                if(!block.equals(Blocks.AIR)&& !block.equals(Blocks.CAVE_AIR)&& !block.equals(Blocks.VOID_AIR)) {
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    level.removeBlockEntity(pos);

                    ICustomPlayerData playerData = (ICustomPlayerData) serverPlayer;
                    if(playerData.katamariIO$getFlag()){
                        float radius = playerData.katamariIO$getSize()/2.0f;

                        Vec3 center = playerPos.add(0, radius, 0);

                        // 相対位置（ワールド座標）
                        Vec3 relativePosWorld = new Vec3(pos.getX()+0.5f, pos.getY()+0.5f, pos.getZ()+0.5f).subtract(center);
                        Quaternionf ballRotation = playerData.katamariIO$getQuaternion();
                        // ボールの逆回転
                        Quaternionf inverseBallRotation = new Quaternionf(ballRotation).invert();

                        // 相対位置（ボールローカル座標）
                        Vector3f relativePosLocal = new Vector3f((float) relativePosWorld.x, (float) relativePosWorld.y, (float) relativePosWorld.z);
                        relativePosLocal = relativePosLocal.rotate(inverseBallRotation);

                        Vec3 localPos = new Vec3(relativePosLocal.x, relativePosLocal.y, relativePosLocal.z);
                        playerData.katamariIO$addBlock(block, inverseBallRotation, localPos);

                        // Sync the new block to the client (totalBatches=-1 means append only, no clear)
                        ModNetwork.CHANNEL.send(
                                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer),
                                new S2CBlockListPacket(
                                        serverPlayer.getId(), 0, -1,
                                        java.util.List.of(block),
                                        java.util.List.of(localPos),
                                        java.util.List.of(inverseBallRotation)));
                    }
                }

            }
        });
        context.setPacketHandled(true);
    }
}

