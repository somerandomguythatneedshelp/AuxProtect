package dev.heliosares.auxprotect;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.bukkit.Bukkit;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.Acrobot.ChestShop.ChestShop;
import com.spawnchunk.auctionhouse.AuctionHouse;

import net.brcdev.shopgui.ShopGuiPlugin;
import net.milkbowl.vault.economy.Economy;
import dev.heliosares.auxprotect.command.APCommand;
import dev.heliosares.auxprotect.command.APCommandTab;
import dev.heliosares.auxprotect.command.ClaimInvCommand;
import dev.heliosares.auxprotect.command.PurgeCommand;
import dev.heliosares.auxprotect.database.DatabaseRunnable;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.SQLiteManager;
import dev.heliosares.auxprotect.listeners.*;
import dev.heliosares.auxprotect.utils.InvSerialization;
import dev.heliosares.auxprotect.utils.Language;
import dev.heliosares.auxprotect.utils.MyPermission;
import dev.heliosares.auxprotect.utils.UpdateChecker;
import dev.heliosares.auxprotect.utils.YMLManager;

/*-
 * TODO: 
 * 
 * Auctionhouse logging
 * better api
 * 
 * */

public class AuxProtect extends JavaPlugin implements IAuxProtect {
	public static final char LEFT_ARROW = 9668;
	public static final char RIGHT_ARROW = 9658;

	public static IAuxProtect getInstance() {
		return instance;
	}

	public DatabaseRunnable dbRunnable;
	public static Language lang;
	public int debug;
	public HashMap<String, Long> lastLogOfInventoryForUUID = new HashMap<>();
	public HashMap<String, Long> lastLogOfMoneyForUUID = new HashMap<>();
	public YMLManager data;
	public APConfig config;

	private Economy econ;
	private static AuxProtect instance;

	SQLiteManager sqlManager;

	public String update;
	long lastCheckedForUpdate;

	@Override
	public void onEnable() {
		instance = this;
		AuxProtectApi.setInstance(this);
		this.getConfig().options().copyDefaults(true);
		this.saveDefaultConfig();
		this.saveConfig();

		YMLManager langManager = new YMLManager("en-us", this);
		langManager.load(true);
		lang = new Language(langManager.getData());

		data = new YMLManager("data", this);
		data.load(false);

		config = new APConfig(this.getConfig());
		config.load();

		debug = getConfig().getInt("debug", 0);

		File sqliteFile = new File(getDataFolder(), "database/auxprotect.db");
		if (!sqliteFile.getParentFile().exists()) {
			if (!sqliteFile.getParentFile().mkdirs()) {
				this.getLogger().severe("Failed to create database directory.");
				this.setEnabled(false);
				return;
			}
		}
		if (!sqliteFile.exists()) {
			try {
				if (!sqliteFile.createNewFile()) {
					throw new IOException();
				}
			} catch (IOException e) {
				this.getLogger().severe("Failed to create database file.");
				this.setEnabled(false);
				return;
			}
		}
		sqlManager = new SQLiteManager(this, "jdbc:sqlite:" + sqliteFile.getAbsolutePath());
		new BukkitRunnable() {

			@Override
			public void run() {
				if (!sqlManager.connect()) {
					getLogger().severe("Failed to connect to SQL database. Disabling.");
					setEnabled(false);
					return;
				}

				for (Object command : getConfig().getList("purge-cmds")) {
					String cmd = (String) command;
					PurgeCommand purge = new PurgeCommand(AuxProtect.this);
					String[] argsOld = cmd.split(" ");
					String[] args = new String[argsOld.length + 1];
					args[0] = "purge";
					for (int i = 0; i < argsOld.length; i++) {
						args[i + 1] = argsOld[i];
					}
					purge.purge(Bukkit.getConsoleSender(), args);
				}
			}
		}.runTaskAsynchronously(this);

		if (!setupEconomy()) {
			getLogger().info("Not using vault");
		}

		dbRunnable = new DatabaseRunnable(this, sqlManager);

		getServer().getScheduler().runTaskTimerAsynchronously(this, dbRunnable, 60, 5);

		new BukkitRunnable() {

			@Override
			public void run() {
				for (Player player : Bukkit.getOnlinePlayers()) {
					Long lastInv = lastLogOfInventoryForUUID.get(player.getUniqueId().toString());
					if (lastInv == null || System.currentTimeMillis() - lastInv > 1000 * 60 * 60) {
						lastLogOfInventoryForUUID.put(player.getUniqueId().toString(), System.currentTimeMillis());
						dbRunnable.add(new DbEntry(AuxProtect.getLabel(player), EntryAction.INVENTORY, false,
								player.getLocation(), "periodic", InvSerialization.playerToBase64(player)));
					}

					dbRunnable.add(new DbEntry("$" + player.getUniqueId().toString(), EntryAction.POS, false,
							player.getLocation(), "", "Y:" + Math.round(player.getLocation().getYaw()) + " P:"
									+ Math.round(player.getLocation().getPitch())));

					lastInv = lastLogOfMoneyForUUID.get(player.getUniqueId().toString());
					if (lastInv == null || System.currentTimeMillis() - lastInv > 1000 * 10 * 60) {
						PlayerListener.logMoney(AuxProtect.this, player, "periodic");
					}
				}
				if (System.currentTimeMillis() - lastCheckedForUpdate > 1000 * 60 * 60) {
					lastCheckedForUpdate = System.currentTimeMillis();
					debug("Checking for updates...");
					String newVersion = UpdateChecker.getVersion(AuxProtect.this, 99147);
					if (newVersion != null) {
						if (!AuxProtect.this.getDescription().getVersion().equals(newVersion)) {
							boolean newUpdate = update == null;
							update = newVersion;
							if (newUpdate) {
								for (Player player : Bukkit.getOnlinePlayers()) {
									if (MyPermission.ADMIN.hasPermission(player)) {
										AuxProtect.this.tellAboutUpdate(player);
									}
								}
								AuxProtect.this.tellAboutUpdate(Bukkit.getConsoleSender());
							}
						}
					}
				}
			}
		}.runTaskTimerAsynchronously(this, 10 * 20, 10 * 20);

		getServer().getPluginManager().registerEvents(new ProjectileListener(this), this);
		getServer().getPluginManager().registerEvents(new EntityListener(this), this);
		getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
		getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
		getServer().getPluginManager().registerEvents(new PaneListener(this), this);

		Plugin plugin = getServer().getPluginManager().getPlugin("ShopGuiPlus");
		if (plugin != null && plugin.isEnabled() && plugin instanceof ShopGuiPlugin) {
			getServer().getPluginManager().registerEvents(new GuiShopListener(this), this);
		}

		plugin = getServer().getPluginManager().getPlugin("ChestShop");
		if (plugin != null && plugin.isEnabled() && plugin instanceof ChestShop) {
			getServer().getPluginManager().registerEvents(new ChestShopListener(this), this);
		}

		plugin = getServer().getPluginManager().getPlugin("AuctionHouse");
		if (plugin != null && plugin.isEnabled() && plugin instanceof AuctionHouse) {
			getServer().getPluginManager().registerEvents(new AuctionHouseListener(this, (AuctionHouse) plugin), this);
		}

		this.getCommand("claiminv").setExecutor(new ClaimInvCommand(this));
		this.getCommand("auxprotect").setExecutor(new APCommand(this));
		this.getCommand("auxprotect").setTabCompleter(new APCommandTab(this));

		if (!config.isPrivate()) {
			EntryAction.ALERT.setEnabled(false);
			EntryAction.CENSOR.setEnabled(false);
			EntryAction.IGNOREABANDONED.setEnabled(false);
			EntryAction.XRAYCHECK.setEnabled(false);
		}
	}

	public void tellAboutUpdate(CommandSender sender) {
		lang.send(sender, "update", AuxProtect.this.getDescription().getVersion(), update);
	}

	@Override
	public void onDisable() {
		config.save();
		this.saveConfig();
		dbRunnable.run();
		if (sqlManager != null)
			sqlManager.close();
		dbRunnable = null;
		sqlManager = null;
	}

	private boolean setupEconomy() {
		if (getServer().getPluginManager().getPlugin("Vault") == null) {
			return false;
		}
		RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
		if (rsp == null) {
			return false;
		}
		econ = rsp.getProvider();
		return econ != null;
	}

	public void debug(String string) {
		this.getLogger().info(string);
	}

	public void debug(String string, int verbosity) {
		if (debug >= verbosity) {
			this.debug(string);
		}
	}

	public SQLiteManager getSqlManager() {
		return sqlManager;
	}

	public Economy getEconomy() {
		return econ;
	}

	public String formatMoney(double d) {
		if (econ == null) {
			return "$" + (Math.round(d * 100) / 100.0);
		}
		return econ.format(d);
	}

	public static String getLabel(Object o) {
		if (o == null) {
			return "#null";
		}
		if (o instanceof Player) {
			return "$" + ((Player) o).getUniqueId().toString();
		} else if (o instanceof Entity) {
			return "#" + ((Entity) o).getType().name().toLowerCase();
		}
		if (o instanceof Container) {
			return "#" + ((Container) o).getBlock().getType().toString().toLowerCase();
		}
		return "#null";
	}

	@Override
	public String translate(String key) {
		return lang.translate(key);
	}

	@Override
	public void warning(String message) {
		getLogger().warning(message);
	}

	@Override
	public boolean isBungee() {
		return false;
	}

	@Override
	public int getDebug() {
		return debug;
	}

	public APConfig getAPConfig() {
		return config;
	}
}
