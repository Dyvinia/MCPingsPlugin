package net.dyvinia.mcpingsplugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class MCPingsPlugin extends JavaPlugin implements PluginMessageListener {
    public final String C2S_JOIN = "mcpings-c2s:join";

    public final String C2S_PING = "mcpings-c2s:ping";
    public final String S2C_PING = "mcpings-s2c:ping";

    private List<Player> moddedPlayers = new ArrayList<>();

    @Override
    public void onEnable() {
        this.saveDefaultConfig();

        this.getServer().getMessenger().registerIncomingPluginChannel(this, C2S_JOIN, this);
        this.getServer().getMessenger().registerIncomingPluginChannel(this, C2S_PING, this);
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, S2C_PING);
    }

    @Override
    public void onDisable() {
        this.saveConfig();

        this.getServer().getMessenger().unregisterIncomingPluginChannel(this);
        this.getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("togglepings")) {
            if (sender instanceof Player p) {
                List<String> pingBlacklist = this.getConfig().getStringList("pingBlacklist");

                if (pingBlacklist.contains(p.getUniqueId().toString())) {
                    pingBlacklist.remove(p.getUniqueId().toString());
                    p.sendMessage(ChatColor.GOLD + "Enabled Pings");
                }
                else {
                    pingBlacklist.add(p.getUniqueId().toString());
                    p.sendMessage(ChatColor.GOLD + "Disabled Pings");
                }

                this.getConfig().set("pingBlacklist", pingBlacklist);
                this.saveConfig();
            }
        }

        return true;
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
                if (this.getConfig().getStringList("pingBlacklist").contains(p.getUniqueId().toString())) {
                    continue;
                }
                if (!p.getWorld().equals(player.getWorld())) {
                    continue;
                }

                if (moddedPlayers.contains(p)) {
                    p.sendPluginMessage(this, S2C_PING, message);
                }
                else if (this.getConfig().getBoolean("serverPings.enabled")) {
                    ByteBuffer in = ByteBuffer.wrap(message);

                    Location loc = new Location(p.getWorld(), in.getDouble(), in.getDouble(), in.getDouble());

                    // Is there a better way to do this? in.readUTF() doesn't work properly since it uses modified UTF
                    String text = StandardCharsets.UTF_8.decode(in).toString();
                    String[] textArray = text
                            .replace("\u0009", "\u0000")
                            .replace("\u0008", "\u0000")
                            .replace("\u0004", "\u0000")
                            .split("\u0000");

                    String pingChannel = textArray[1];
                    String username = textArray[2];

                    if (!pingChannel.equals("")) continue; // only show pings on global channel

                    int duration = this.getConfig().getInt("serverPings.pingDuration");

                    // Ping Icon
                    createTextDisplay(p, loc.add(new Vector(0f, -0.2f, 0f)), duration, "\u2022", 0x00000000);

                    // Ping Distance
                    int refreshRate = this.getConfig().getInt("serverPings.pingRefreshRate");
                    double distance = player.getLocation().distance(loc);
                    int distanceDisplay = createTextDisplay(p, loc.add(new Vector(0f, 0.35f, 0f)), duration, String.format("%.1fm", distance), 0x87000000);
                    new BukkitRunnable() {
                        int count = 0;
                        public void run() {
                            count++;
                            updateTextDisplay(p, distanceDisplay, String.format("%.1fm", player.getLocation().distance(loc)));
                            if (count > duration * 20/refreshRate)
                                cancel();
                        }
                    }.runTaskTimer(this, 0, refreshRate);

                    // Ping Username
                    if (this.getConfig().getBoolean("serverPings.showPingUsername"))
                        createTextDisplay(p, loc.add(new Vector(0f, 0.35f, 0f)), duration, username, 0x87000000);

                    // Ping Sound
                    p.playNote(loc, Instrument.BELL, Note.natural(0, Note.Tone.D));
                }
            }
        }
    }

    private int createTextDisplay(Player player, Location loc, int duration, String text, Integer backgroundColor) {
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

        return entId;
    }

    private void updateTextDisplay(Player player, int entId, String text) {
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

        PacketType typeMeta = PacketType.Play.Server.ENTITY_METADATA;
        PacketContainer packetMeta = ProtocolLibrary.getProtocolManager().createPacket(typeMeta);

        packetMeta.getIntegers().write(0, entId);

        WrappedDataWatcher.Serializer chatSerializer = WrappedDataWatcher.Registry.getChatComponentSerializer();

        List<WrappedDataValue> dataValues = new ArrayList<>();

        Object optChat = WrappedChatComponent.fromText(text).getHandle();
        dataValues.add(new WrappedDataValue(22, chatSerializer, optChat));

        packetMeta.getDataValueCollectionModifier().write(0, dataValues);

        protocolManager.sendServerPacket(player, packetMeta);
    }
}
