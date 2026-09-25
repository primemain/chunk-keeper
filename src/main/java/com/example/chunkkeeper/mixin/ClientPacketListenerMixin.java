package com.example.chunkkeeper.mixin;

import com.example.chunkkeeper.ChunkKeeper;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    /** Runs before Sodium's hooks, which sit further down in the chunk drop code we skip. */
    @Inject(method = "handleForgetLevelChunk", at = @At("HEAD"), cancellable = true)
    private void chunkkeeper$keepChunk(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
        if (ChunkKeeper.shouldKeep(packet.pos())) {
            ci.cancel();
        }
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At("HEAD"))
    private void chunkkeeper$serverSentChunk(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        ChunkKeeper.onServerLoaded(new ChunkPos(packet.getX(), packet.getZ()));
    }
}
