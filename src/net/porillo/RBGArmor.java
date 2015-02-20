package net.porillo;

import static java.lang.Math.PI;
import static java.lang.Math.sin;
import static net.porillo.Utility.getWorker;
import static net.porillo.Utility.send;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.porillo.commands.CommandHandler;
import net.porillo.workers.Worker;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class RBGArmor extends JavaPlugin implements Listener {

    private CommandHandler handler = new CommandHandler(this);
    private Map<UUID, DebugWindow> debuggers;
    private Map<UUID, Worker> workerz; 
    private static Config config;
    public static Color[] rb;

    @Override
    public void onEnable() {
        workerz = new HashMap<UUID, Worker>();
        debuggers = new HashMap<UUID, DebugWindow>();
        config = new Config(this);
        rb = new Color[config.getColors()];
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {

            @Override
            public void run() {
                final int colors = config.getColors();
                final double f = (6.48 / (double) colors);
                for (int i = 0; i < colors; ++i) {
                    double r = sin(f * i + 0.0D) * 127.0D + 128.0D;
                    double g = sin(f * i + (2 * PI / 3)) * 127.0D + 128.0D;
                    double b = sin(f * i + (4 * PI / 3)) * 127.0D + 128.0D;
                    rb[i] = Color.fromRGB((int) r, (int) g, (int) b);
                }
                if (config.shouldDebug()) {
                    getLogger().info("------ RGBArmor Debug ------");
                    getLogger().info("- Using " + config.getColors() + " colors");
                    int ups = 20 / config.getRefreshRate();
                    getLogger().info("- Armor updates " + ups + " times per second");

                }
            }
        });
        this.loadLang();
    }
    
    @Override
    public void onDisable() {
        workerz.clear();
        Bukkit.getScheduler().cancelTasks(this);
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        handler.runCommand(s, l, a);
        return true;
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent e) {
        final UUID uuid = e.getPlayer().getUniqueId();
        if (workerz.containsKey(uuid)) {
            Worker w = workerz.get(uuid);
            Bukkit.getScheduler().cancelTask(w.getUniqueId());
            workerz.remove(uuid);
        }
    }
    
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        final UUID uuid = e.getEntity().getUniqueId();
        if (workerz.containsKey(uuid)) {
            Worker w = workerz.get(uuid);
            Bukkit.getScheduler().cancelTask(w.getUniqueId());
            workerz.remove(uuid);
            send(e.getEntity(), "death");
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!workerz.containsKey(p.getUniqueId())) {
            for (ItemStack is : p.getInventory().getArmorContents()) {
                ItemMeta meta;
                if (is != null && (meta = is.getItemMeta()) instanceof LeatherArmorMeta) {
                    if (meta.hasLore()) {
                        Worker worker = getWorker(p, meta.getLore());
                        if (worker != null) {
                            this.initWorker(p, worker);
                            return;
                        }
                    }
                }
            }
        }
    }

    private void initWorker(Player p, Worker rw) {
        int rr = config.getRefreshRate();
        BukkitTask id = Bukkit.getScheduler().runTaskTimer(this, rw, rr, rr);
        rw.setUniqueId(id.getTaskId());
        workerz.put(p.getUniqueId(), rw);
        send(p, "&aYour armor is activated, using &b" + rw.getType() + " &acoloring.");
        send(p, "&dUse /rg off or logout to stop armor coloring!");
    }
    
    public void loadLang() {
        File lang = new File(getDataFolder(), "lang.yml");
        OutputStream out = null;
        InputStream defLangStream = this.getResource("lang.yml");
        if (!lang.exists()) {
            try {
                getDataFolder().mkdir();
                lang.createNewFile();
                if (defLangStream != null) {
                    out = new FileOutputStream(lang);
                    int read;
                    byte[] bytes = new byte[1024];
                    while ((read = defLangStream.read(bytes)) != -1) {
                        out.write(bytes, 0, read);
                    }
                    YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(defLangStream);
                    Lang.setFile(defConfig);
                }
            } catch (IOException e) {
                e.printStackTrace(); 
                getLogger().severe("[RGBArmor] Couldn't create language file.");
                this.setEnabled(false); 
            } finally {
                if (defLangStream != null) {
                    try {
                        defLangStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        YamlConfiguration conf = YamlConfiguration.loadConfiguration(lang);
        for (Lang item : Lang.values()) {
            if (conf.getString(item.getPath()) == null) {
                conf.set(item.getPath(), item.getDefault());
            }
        }
        Lang.setFile(conf);
        try {
            conf.save(lang);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public Config getOurConfig() {
        return config;
    }
    
    public Map<UUID, Worker> getWorkers() {
        return this.workerz;
    }
    
    public Map<UUID, DebugWindow> getDebuggers() {
        return this.debuggers;
    }
}