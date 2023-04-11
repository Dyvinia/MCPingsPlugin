package net.dyvinia.mcpingsplugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class MCPingsPlugin extends JavaPlugin implements PluginMessageListener {

    public final String C2S_PING = "mcpings-c2s:ping";
    public final String S2C_PING = "mcpings-s2c:ping";

    @Override
    public void onEnable() {
        getLogger().info("MCPings Server Init");

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
        if (channel.equals(C2S_PING)) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendPluginMessage(this, S2C_PING, message);
            }
        }
    }
}
