package dev.heliosares.auxprotect.command;

import java.util.HashMap;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import dev.heliosares.auxprotect.AuxProtect;
import dev.heliosares.auxprotect.database.Results;

public class TpCommand implements CommandExecutor {

	private AuxProtect plugin;

	public TpCommand(AuxProtect plugin) {
		this.plugin = plugin;
		results = new HashMap<>();
	}

	HashMap<String, Results> results;

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length < 5) {
			return true;
		}
		if (!(sender instanceof Player)) {
			return true;
		}
		Player player = (Player) sender;
		try {
			int x = Integer.parseInt(args[1]);
			int y = Integer.parseInt(args[2]);
			int z = Integer.parseInt(args[3]);
			World world = plugin.getServer().getWorld(args[4]);
			if (world != null) {
				final Location target = new Location(world, x + 0.5, y, z + 0.5);
				player.teleport(target);
				if (player.getGameMode() == GameMode.SPECTATOR) {
					new BukkitRunnable() {
						int tries;

						@Override
						public void run() {
							if (tries++ >= 5 || player.getLocation().distance(target) < 2) {
								this.cancel();
								return;
							}
							player.teleport(target);
						}
					}.runTaskTimer(plugin, 2, 1);
				}
			}
		} catch (NumberFormatException e) {

		}
		return true;
	}
}
