package dev.heliosares.auxprotect.spigot.listeners;

import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.BlockProjectileSource;

import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.InvSerialization;

public class ProjectileListener implements Listener {

	private AuxProtectSpigot plugin;
	ArrayList<EntityType> whitelist;

	public ProjectileListener(AuxProtectSpigot plugin) {
		this.plugin = plugin;
		this.whitelist = new ArrayList<>();
		whitelist.add(EntityType.ENDER_PEARL);
		whitelist.add(EntityType.TRIDENT);
		whitelist.add(EntityType.FISHING_HOOK);
		whitelist.add(EntityType.SNOWBALL);
		whitelist.add(EntityType.EGG);
		whitelist.add(EntityType.SPLASH_POTION);
		whitelist.add(EntityType.ARROW);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onProjectileLaunchEvent(ProjectileLaunchEvent e) {
		logEntity(null, e.getEntity(), EntryAction.LAUNCH, true);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onProjectileHit(ProjectileHitEvent e) {
		if (e.getHitBlock() == null) {
			return;
		}
		logEntity(null, e.getEntity(), EntryAction.LAND, false);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerPickupArrowEvent(PlayerPickupArrowEvent e) {
		logEntity(e.getPlayer(), e.getArrow(), EntryAction.GRAB, false);
	}

	private void logEntity(LivingEntity actor, Projectile entity, EntryAction action, boolean logData) {
		if (entity == null || !whitelist.contains(entity.getType())) {
			return;
		}
		String actorLabel = null;
		Location location = null;
		if (actor == null) {
			if (entity.getShooter() == null) {
				return;
			}
			if (entity.getShooter() instanceof BlockProjectileSource) {
				BlockProjectileSource shooter = (BlockProjectileSource) entity.getShooter();
				actorLabel = AuxProtectSpigot.getLabel(shooter.getBlock());
				location = shooter.getBlock().getLocation();
			} else if (entity.getShooter() instanceof LivingEntity) {
				LivingEntity shooter = (LivingEntity) entity.getShooter();
				actorLabel = AuxProtectSpigot.getLabel(shooter);
				location = shooter.getLocation();
			} else {
				return;
			}
		} else {
			actorLabel = AuxProtectSpigot.getLabel(actor);
			location = actor.getLocation();
		}
		ItemStack item = null;
		if (entity instanceof ThrowableProjectile) {
			item = ((ThrowableProjectile) entity).getItem();
		} else if (entity instanceof ThrownPotion) {
			item = ((ThrownPotion) entity).getItem();
		}

		DbEntry entry = new DbEntry(actorLabel, action, false, location, AuxProtectSpigot.getLabel(entity), "");

		if (item != null && logData && InvSerialization.isCustom(item)) {
			try {
				entry.setBlob(InvSerialization.toByteArray(item));
			} catch (Exception e1) {
				plugin.warning("Error serializing projectile");
				plugin.print(e1);
			}
		}
		plugin.add(entry);
	}
}
