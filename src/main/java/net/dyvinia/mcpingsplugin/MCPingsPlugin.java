package net.dyvinia.mcpingsplugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Note;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class MCPingsPlugin extends JavaPlugin implements PluginMessageListener {
    public final String C2S_JOIN = "mcpings-c2s:join";

    public final String C2S_PING = "mcpings-c2s:ping";
    public final String S2C_PING = "mcpings-s2c:ping";

    private List<Player> moddedPlayers = new ArrayList<>();

    @Override
    public void onEnable() {
        getLogger().info("MCPings Server Init");

        this.getServer().getMessenger().registerIncomingPluginChannel(this, C2S_JOIN, this);
        this.getServer().getMessenger().registerIncomingPluginChannel(this, C2S_PING, this);
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, S2C_PING);
    }

    @Override
    public void onDisable() {
        this.getServer().getMessenger().unregisterIncomingPluginChannel(this);
        this.getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (channel.equals(C2S_JOIN)) {
            if (!moddedPlayers.contains(player)) {
                moddedPlayers.add(player);
            }
        }
        else if (channel.equals(C2S_PING)) {
            if (!moddedPlayers.contains(player)) {
                moddedPlayers.add(player);
            }

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (moddedPlayers.contains(p)) {
                    p.sendPluginMessage(this, S2C_PING, message);
                }
                else {
                    ByteArrayDataInput in = ByteStreams.newDataInput(message);

                    Location loc = new Location(p.getWorld(), in.readDouble(), in.readDouble(), in.readDouble());

                    String text = in.readUTF();

                    newTextDisplay(p, loc, 5, "\u2022", 0x00000000);
                    newTextDisplay(p, loc.add(new Vector(0f, 0.25f, 0f)), 5, text, 0x40000000);
                    p.playNote(loc, Instrument.BELL, Note.natural(0, Note.Tone.D));
                }
            }
        }
    }

    private void newTextDisplay(Player player, Location loc, int duration, String text, Integer backgroundColor) {
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

        int entId = ThreadLocalRandom.current().nextInt();
        UUID entUuid = UUID.randomUUID();

        PacketType type = PacketType.Play.Server.SPAWN_ENTITY;
        PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(type);

        StructureModifier<Integer> intMod = packet.getIntegers();
        StructureModifier<EntityType> typeMod = packet.getEntityTypeModifier();
        StructureModifier<UUID> uuidMod = packet.getUUIDs();
        StructureModifier<Double> doubleMod = packet.getDoubles();

        // Write id of entity
        intMod.write(0, entId);

        // Write type of entity
        typeMod.write(0, EntityType.TEXT_DISPLAY);

        // Write entities UUID
        uuidMod.write(0, entUuid);

        // Write position
        doubleMod.write(0, loc.getX());
        doubleMod.write(1, loc.getY());
        doubleMod.write(2, loc.getZ());

        protocolManager.sendServerPacket(player, packet);

        PacketType typeMeta = PacketType.Play.Server.ENTITY_METADATA;
        PacketContainer packetMeta = ProtocolLibrary.getProtocolManager().createPacket(typeMeta);

        packetMeta.getIntegers().write(0, entId);

        WrappedDataWatcher.Serializer byteSerializer = WrappedDataWatcher.Registry.get(Byte.class);
        WrappedDataWatcher.Serializer chatSerializer = WrappedDataWatcher.Registry.getChatComponentSerializer();
        WrappedDataWatcher.Serializer intSerializer = WrappedDataWatcher.Registry.get(Integer.class);

        List<WrappedDataValue> dataValues = new ArrayList<>();

        Byte billboard = 3;
        dataValues.add(new WrappedDataValue(14, byteSerializer, billboard));

        Object optChat = WrappedChatComponent.fromText(text).getHandle();
        dataValues.add(new WrappedDataValue(22, chatSerializer, optChat));

        dataValues.add(new WrappedDataValue(24, intSerializer, backgroundColor));

        Byte seeThrough = 0x02;
        dataValues.add(new WrappedDataValue(26, byteSerializer, seeThrough));

        packetMeta.getDataValueCollectionModifier().write(0, dataValues);

        protocolManager.sendServerPacket(player, packetMeta);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            PacketType typeKill = PacketType.Play.Server.ENTITY_DESTROY;
            PacketContainer packetKill = ProtocolLibrary.getProtocolManager().createPacket(typeKill);

            List<Integer> intList = new ArrayList<>();
            intList.add(entId);

            packetKill.getIntLists().write(0, intList);
            protocolManager.sendServerPacket(player, packetKill);
        }, 20L * duration);
    }
}
