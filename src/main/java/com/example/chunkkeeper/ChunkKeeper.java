package com.example.chunkkeeper;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Keeps chunks on screen after the server tells the client to forget them.
 *
 * When the server says "forget this chunk" we ignore it and remember the chunk instead.
 * Only the newest MAX_KEPT such chunks are kept, and only while they are within your render
 * distance and no further than HARD_MAX_DISTANCE (the vanilla client can only hold chunks up to
 * 5 chunks away from your position). Anything older or further away is forgotten the normal way.
 */
public class ChunkKeeper implements ClientModInitializer {
    /** How many extra chunks (on top of the ones the server keeps loaded) we hold on to. */
    public static final int MAX_KEPT = 16;
    /** Vanilla cannot hold chunks more than 5 away, so we never keep any further than this. */
    public static final int HARD_MAX_DISTANCE = 4;

    /** Oldest first. Only touched on the client thread. */
    private static final Set<ChunkPos> kept = new LinkedHashSet<>();
    private static ClientLevel trackedLevel;
    /** True while we are releasing a chunk ourselves, so our own hook lets it through. */
    private static boolean releasing;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(ChunkKeeper::tick);
    }

    /** Called when the server sends "forget this chunk". Returns true to ignore the message. */
    public static boolean shouldKeep(ChunkPos pos) {
        Minecraft mc = Minecraft.getInstance();
        // The message first arrives on the network thread and is re-run on the client thread. Only act on the second run.
        if (!mc.isSameThread() || releasing || mc.level == null || mc.player == null) return false;
        syncLevel(mc.level);
        if (distance(pos, mc.player.chunkPosition()) > maxDistance(mc)) return false;
        kept.remove(pos);
        kept.add(pos);
        return true;
    }

    /** Called when the server sends a chunk. The server owns it again, so we stop tracking it. */
    public static void onServerLoaded(ChunkPos pos) {
        if (Minecraft.getInstance().isSameThread()) kept.remove(pos);
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.getConnection() == null) {
            kept.clear();
            trackedLevel = null;
            return;
        }
        syncLevel(mc.level);

        ChunkPos player = mc.player.chunkPosition();
        int maxDistance = maxDistance(mc);
        Iterator<ChunkPos> it = kept.iterator();
        while (it.hasNext()) {
            ChunkPos pos = it.next();
            boolean gone = mc.level.getChunkSource().getChunk(pos.x, pos.z, ChunkStatus.FULL, false) == null;
            if (gone) {
                it.remove();
            } else if (distance(pos, player) > maxDistance) {
                it.remove();
                release(mc, pos);
            }
        }
        while (kept.size() > MAX_KEPT) {
            ChunkPos oldest = kept.iterator().next();
            kept.remove(oldest);
            release(mc, oldest);
        }
    }

    /** Forget a chunk the normal vanilla way (drops it, removes its light, lets Sodium know). */
    private static void release(Minecraft mc, ChunkPos pos) {
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) return;
        releasing = true;
        try {
            connection.handleForgetLevelChunk(new ClientboundForgetLevelChunkPacket(pos));
        } finally {
            releasing = false;
        }
    }

    /** A new world or dimension has its own chunks, so start over. */
    private static void syncLevel(ClientLevel level) {
        if (level != trackedLevel) {
            kept.clear();
            trackedLevel = level;
        }
    }

    /** Follows your render distance setting, but never goes past what the game can hold. */
    private static int maxDistance(Minecraft mc) {
        return Math.min(mc.options.renderDistance().get(), HARD_MAX_DISTANCE);
    }

    private static int distance(ChunkPos a, ChunkPos b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z));
    }
}
