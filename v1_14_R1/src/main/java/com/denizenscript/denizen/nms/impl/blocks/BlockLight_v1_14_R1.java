package com.denizenscript.denizen.nms.impl.blocks;

import com.denizenscript.denizen.nms.NMSHandler;
import com.denizenscript.denizen.nms.abstracts.BlockLight;
import com.denizenscript.denizen.nms.util.ReflectionHelper;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import net.minecraft.server.v1_14_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_14_R1.CraftChunk;
import org.bukkit.craftbukkit.v1_14_R1.block.CraftBlock;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class BlockLight_v1_14_R1 extends BlockLight {

    private static final Field PACKETPLAYOUTLIGHTUPDATE_CHUNKX = ReflectionHelper.getFields(PacketPlayOutLightUpdate.class).get("a");
    private static final Field PACKETPLAYOUTLIGHTUPDATE_CHUNKZ = ReflectionHelper.getFields(PacketPlayOutLightUpdate.class).get("b");
    private static final Field PACKETPLAYOUTLIGHTUPDATE_BLOCKIGHT_BITMASK = ReflectionHelper.getFields(PacketPlayOutLightUpdate.class).get("d");
    private static final Field PACKETPLAYOUTLIGHTUPDATE_BLOCKIGHT_DATA = ReflectionHelper.getFields(PacketPlayOutLightUpdate.class).get("h");
    public static final Field PACKETPLAYOUTBLOCKCHANGE_POSITION = ReflectionHelper.getFields(PacketPlayOutBlockChange.class).get("a");

    private BlockLight_v1_14_R1(Location location, long ticks) {
        super(location, ticks);
    }

    public static BlockLight createLight(Location location, int lightLevel, long ticks) {
        location = location.getBlock().getLocation();
        BlockLight blockLight;
        if (lightsByLocation.containsKey(location)) {
            blockLight = lightsByLocation.get(location);
            if (blockLight.removeTask != null) {
                blockLight.removeTask.cancel();
                blockLight.removeTask = null;
            }
            blockLight.removeLater(ticks);
        }
        else {
            blockLight = new BlockLight_v1_14_R1(location, ticks);
            lightsByLocation.put(location, blockLight);
            if (!lightsByChunk.containsKey(blockLight.chunk)) {
                lightsByChunk.put(blockLight.chunk, new ArrayList<>());
            }
            lightsByChunk.get(blockLight.chunk).add(blockLight);
        }
        blockLight.intendedLevel = lightLevel;
        blockLight.update(lightLevel, true);
        return blockLight;
    }

    public static void checkIfLightsBrokenByPacket(PacketPlayOutBlockChange packet, World world) {
        try {
            BlockPosition pos = (BlockPosition) PACKETPLAYOUTBLOCKCHANGE_POSITION.get(packet);
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            Bukkit.getScheduler().scheduleSyncDelayedTask(NMSHandler.getJavaPlugin(), () -> {
                Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                boolean any = false;
                for (Vector vec : RELATIVE_CHUNKS) {
                    Chunk other = world.getChunkIfLoaded(chunkX + vec.getBlockX(), chunkZ + vec.getBlockZ());
                    if (other != null) {
                        List<BlockLight> lights = lightsByChunk.get(other.bukkitChunk);
                        if (lights != null) {
                            any = true;
                            for (BlockLight light : lights) {
                                light.update(light.intendedLevel, false);
                            }
                        }
                    }
                }
                if (any) {
                    Bukkit.getScheduler().scheduleSyncDelayedTask(NMSHandler.getJavaPlugin(), () -> sendNearbyChunkUpdates(chunk), 1);
                }
            }, 1);
        }
        catch (Exception ex) {
            Debug.echoError(ex);
        }
    }

    public static void checkIfLightsBrokenByPacket(PacketPlayOutLightUpdate packet, World world) {
        if (doNotCheck) {
            return;
        }
        try {
            int cX = PACKETPLAYOUTLIGHTUPDATE_CHUNKX.getInt(packet);
            int cZ = PACKETPLAYOUTLIGHTUPDATE_CHUNKZ.getInt(packet);
            int bitMask = PACKETPLAYOUTLIGHTUPDATE_BLOCKIGHT_BITMASK.getInt(packet);
            List<byte[]> blockData = (List<byte[]>) PACKETPLAYOUTLIGHTUPDATE_BLOCKIGHT_DATA.get(packet);
            Bukkit.getScheduler().scheduleSyncDelayedTask(NMSHandler.getJavaPlugin(), () -> {
                Chunk chk = world.getChunkIfLoaded(cX, cZ);
                if (chk == null) {
                    return;
                }
                List<BlockLight> lights = lightsByChunk.get(chk.bukkitChunk);
                if (lights == null) {
                    return;
                }
                boolean any = false;
                for (BlockLight light : lights) {
                    if (((BlockLight_v1_14_R1) light).checkIfChangedBy(bitMask, blockData)) {
                        light.update(light.intendedLevel, false);
                        any = true;
                    }
                }
                if (any) {
                    Bukkit.getScheduler().scheduleSyncDelayedTask(NMSHandler.getJavaPlugin(), () -> sendNearbyChunkUpdates(chk), 1);
                }
            }, 1);
        }
        catch (Exception ex) {
            Debug.echoError(ex);
        }
    }

    public static boolean doNotCheck = false;

    public boolean checkIfChangedBy(int bitmask, List<byte[]> data) {
        Location blockLoc = block.getLocation();
        int layer = (blockLoc.getBlockY() >> 4) + 1;
        if ((bitmask & (1 << layer)) == 0) {
            return false;
        }
        int found = 0;
        for (int i = 0; i < 16; i++) {
            if ((bitmask & (1 << i)) != 0) {
                if (i == layer) {
                    byte[] blocks = data.get(found);
                    NibbleArray arr = new NibbleArray(blocks);
                    int x = blockLoc.getBlockX() - (chunk.getX() << 4);
                    int y = blockLoc.getBlockY() % 16;
                    int z = blockLoc.getBlockZ() - (chunk.getZ() << 4);
                    int level = arr.a(x, y, z);
                    return intendedLevel != level;
                }
                found++;
            }
        }
        return false;
    }

    @Override
    public void update(int lightLevel, boolean updateChunk) {
        LightEngine lightEngine = ((CraftChunk) chunk).getHandle().e();
        ((LightEngineBlock) lightEngine.a(EnumSkyBlock.BLOCK)).a(((CraftBlock) block).getPosition(), lightLevel);
        if (updateChunk) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(NMSHandler.getJavaPlugin(), this::sendNearbyChunkUpdates, 1);
        }
    }

    public static final Vector[] RELATIVE_CHUNKS = new Vector[] {
            new Vector(0, 0, 0),
            new Vector(-1, 0, 0), new Vector(1, 0, 0), new Vector(0, 0, -1), new Vector(0, 0, 1),
            new Vector(-1, 0, -1), new Vector(-1, 0, 1), new Vector(1, 0, -1), new Vector(1, 0, 1)
    };

    public  void sendNearbyChunkUpdates() {
        sendNearbyChunkUpdates(((CraftChunk) chunk).getHandle());
    }

    public static void sendNearbyChunkUpdates(Chunk chunk) {
        ChunkCoordIntPair pos = chunk.getPos();
        for (Vector vec : RELATIVE_CHUNKS) {
            Chunk other = chunk.getWorld().getChunkIfLoaded(pos.x + vec.getBlockX(), pos.z + vec.getBlockZ());
            if (other != null) {
                sendSingleChunkUpdate(other);
            }
        }
    }

    public static void sendSingleChunkUpdate(Chunk chunk) {
        doNotCheck = true;
        LightEngine lightEngine = chunk.e();
        ChunkCoordIntPair pos = chunk.getPos();
        PacketPlayOutLightUpdate packet = new PacketPlayOutLightUpdate(pos, lightEngine);
        ((WorldServer) chunk.world).getChunkProvider().playerChunkMap.a(pos, false).forEach((player) -> {
            player.playerConnection.sendPacket(packet);
        });
        doNotCheck = false;
    }
}