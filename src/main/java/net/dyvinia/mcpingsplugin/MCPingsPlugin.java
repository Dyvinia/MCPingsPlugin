package net.dyvinia.mcpingsplugin;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.*;

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
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (moddedPlayers.contains(p)) {
                    p.sendPluginMessage(this, S2C_PING, message);
                }
                else {
                    ByteArrayDataInput in = ByteStreams.newDataInput(message);

                    Location loc = new Location(p.getWorld(), in.readDouble(), in.readDouble(), in.readDouble());

                    // handle ping for vanilla players somehow

                    //Bukkit.getScheduler().runTaskLater(this, () -> something, 20L * 10);
                }
            }
        }
    }
}
