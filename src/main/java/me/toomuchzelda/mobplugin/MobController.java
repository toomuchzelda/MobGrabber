package me.toomuchzelda.mobplugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftPig;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import net.md_5.bungee.api.ChatColor;
import net.minecraft.world.entity.animal.Pig;

/**
 * @author toomuchzelda
 * 
 *
 */
public class MobController implements Listener
{
	public static ItemStack _controllerItem;
	//						user,    target
	private static HashMap<Player, ControlledMob> _controllerMap = new HashMap<>();

	private FileConfiguration config;

	//min and max distance grabber can hold entity from
	public static double minDistance = 1.2;
	public static double maxDistance = 30;

	//for some bad behaviour that doesnt appear in PaperMC
	public static boolean isPaperMC = false;
	
	private static boolean allowedBp = true;

	public MobController(MobPlugin plugin, FileConfiguration config)
	{
		this.config = config;

		this.createControllerItem();

		minDistance = config.getDouble("minimumDistance");
		Bukkit.getLogger().info("Minimum holding distance: " + minDistance);
		maxDistance = config.getDouble("maximumDistance");
		Bukkit.getLogger().info("Maximum holding distance: " + maxDistance);
		Bukkit.getLogger().info("Enabled default crafting recipe: " + config.getBoolean("craftable"));
		allowedBp = config.getBoolean("allow-backpack");
		Bukkit.getLogger().info("Enabled backpack: " + config.getBoolean("allow-backpack"));

		MobPlugin.getMobPlugin().getServer().getPluginManager().registerEvents(this, plugin);
		//https://www.spigotmc.org/threads/test-if-server-is-spigot-or-craftbukkit.96925/
		//https://papermc.io/forums/t/checking-for-server-type-paper-spigot-or-bukkit/981
		try
		{
			isPaperMC = Class.forName("com.destroystokyo.paper.VersionHistoryManager$VersionData") != null;
			Bukkit.getLogger().info("Running PaperMC");
		}
		catch (ClassNotFoundException e)
		{
			Bukkit.getLogger().info("Not running PaperMC");
		}
	}

	private void createControllerItem()
	{
		//create the mobgrabber item (and recipe)
		ItemStack item = new ItemStack(Material.SHULKER_SHELL);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName("Mob Grabber");
		item.setItemMeta(meta);
		_controllerItem = item;

		if(config.getBoolean("craftable"))
		{
			NamespacedKey key = new NamespacedKey(MobPlugin.getMobPlugin(), "mob_grabber");
			ShapedRecipe recipe = new ShapedRecipe(key, _controllerItem);
			//H=Gold Ingot, S=String, B=Bucket, R=Redstone Block
			recipe.shape(" H ", "SBS", " R ");
			recipe.setIngredient('H', Material.GOLD_INGOT);
			recipe.setIngredient('S', Material.STRING);
			recipe.setIngredient('B', Material.BUCKET);
			recipe.setIngredient('R', Material.REDSTONE_BLOCK);

			Bukkit.addRecipe(recipe);
		}
	}

//	@EventHandler
//	public void printInteract(PlayerInteractEvent event)
//	{
//		//if(event.getPlayer().is)
//		event.getPlayer().sendMessage(event.getAction().toString());
//	}

	@EventHandler
	public void onRightClick(PlayerInteractEvent event)
	{
		if(event.getAction().equals(Action.RIGHT_CLICK_AIR) || event.getAction().equals(Action.RIGHT_CLICK_BLOCK))
		{
			if(event.hasItem())
			{
				//compare the item held disregarding amount in stack
				ItemStack item = event.getItem().clone();
				item.setAmount(1);
				if(item.equals(_controllerItem))
				{
					if(_controllerMap.get(event.getPlayer()) == null)
					{
						//perform a raytrace to see if they're targetting an entity or not
						World world = event.getPlayer().getWorld();
						Player p = event.getPlayer();

						//uuuuuuhhhhhh
						Predicate<Entity> notUserOrSpectator = new Predicate<Entity>()
						{

							@Override
							public boolean test(Entity e) {
								if(e.getEntityId() == p.getEntityId())
									return false;
								
								if(e.getCustomName() != null &&
										e.getCustomName().equals("MobGrabberPig" + e.getEntityId()))
									return false;
								
								if(e instanceof Player)
								{
									Player rayPlayer = (Player) e;
									if(rayPlayer.getGameMode().equals(GameMode.SPECTATOR))
										return false;
								}
								
								return true;
							}
						};

						RayTraceResult result = world.rayTrace(p.getEyeLocation(), p.getLocation().getDirection()
								, maxDistance, FluidCollisionMode.NEVER, true, 0.3, notUserOrSpectator);

						double particleDist;
						if(result != null)
							particleDist = p.getEyeLocation().subtract(result.getHitPosition()).length();
						else
							particleDist = maxDistance;
						
						drawParticleLine(p.getEyeLocation(), p.getLocation().getDirection(), particleDist);

						//if the raytrace hit an entity
						if(result != null && result.getHitBlock() == null && result.getHitEntity() != null)
						{
							if(result.getHitEntity() instanceof LivingEntity)
							{
								//p.sendMessage(result.getHitEntity().getName());
								//Vector difference = result.getHitPosition().subtract(p.getEyeLocation().toVector());
								//double distance = difference.length();
								//p.sendMessage("Distance " + distance);
								LivingEntity livent = (LivingEntity) result.getHitEntity();

								if(isControlled(event.getPlayer()) && result.getHitEntity() instanceof Player)
								{
									//Player controller = (Player) result.getHitEntity();
									ControlledMob mob = _controllerMap.get(result.getHitEntity());
									if(mob != null && mob.getMob() == p)
									{
										p.sendMessage("You can't grab someone who's grabbing you!");
									}
								}
								else if(!isControlled(livent))
								{
									//false if main hand, true if offhand
									setControlling(p, livent, !(event.getHand() == EquipmentSlot.HAND));
									p.playSound(p.getLocation(), Sound.ENTITY_LEASH_KNOT_PLACE, 100, 0);
									p.sendMessage(ChatColor.DARK_GRAY + "Grabbed " + livent.getName());

									//p.sendMessage(_controllerMap.toString());
									//p.sendMessage("===================");
								}
								else
								{
									p.sendMessage("Someone else is grabbing " + livent.getName());
								}
							}
						}
					}
					else
					{
						/*
						//setNotControlling(event.getPlayer());

						removeControlledEffects(event.getPlayer());
						//only throw velocity on intentional drops?
						mob.applyVelocity();

						_controllerMap.remove(event.getPlayer());
						*/
						ControlledMob mob = _controllerMap.get(event.getPlayer());
						String grabbedName = mob.getMob().getName();
						event.getPlayer().sendMessage("§8Dropped " + grabbedName);
						event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.ENTITY_LEASH_KNOT_BREAK, 999, 0);
						setNotControlling(event.getPlayer());
					}
				}
			}
		}
	}

	/*@EventHandler
	public void onLeftClick(PlayerInteractEvent event)
	{
		if(event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK)
		{
			
			if(event.getItem() != null && event.getItem().equals(_controllerItem) 
					&& _controllerMap.get(event.getPlayer()) != null)
			{
				event.setCancelled(true);
				
				ControlledMob mob = _controllerMap.get(event.getPlayer());
				
				if(!mob.tossed)
				{
					removeControlledEffects(event.getPlayer());
					Vector toss = mob.getMob().getLocation().toVector().subtract(event.getPlayer()
							.getLocation().toVector());

					toss.multiply(0.3);
					mob.getMob().setVelocity(toss);
					event.getPlayer().sendMessage("§8Threw " + mob.getMob().getName());

					_controllerMap.remove(event.getPlayer());
				}
				else
				{
					//if it was true, the method was called by dropping the item from
					//onDropGrabberItem so don't throw the mob and reset this bool
					mob.tossed = false;
				}
			}
		}
	}
	*/

	public static void setControlling(Player user, LivingEntity target, boolean offHandUsed)
	{
		if(_controllerMap.get(user) == null)
		{
			ControlledMob ctrlMob;

			//calculate distance between grabber/grabbed
			double distance = target.getLocation().toVector().subtract(user.getLocation().toVector()).length();
			if(distance < minDistance)
				distance = minDistance;
			
			//disable backpacking if not allowed in config
			if(!isAllowedBp())
				offHandUsed = false;

			if(!isPaperMC)
			{
				//if(!offHandUsed)
					target.teleport(calculateHeldLoc(user, target, distance));
				//else
				//	target.teleport(calculateBackLoc(user, target));
			}

			int slot = user.getInventory().getHeldItemSlot();

			ctrlMob = new ControlledMob(user, target, distance, slot);

			ctrlMob.mountPlayer();
			
			_controllerMap.put(user, ctrlMob);

			if(offHandUsed)
				ctrlMob.setBackpack();
		}
	}

	public static void setNotControlling(Player user)
	{
		removeControlledEffects(user);
		_controllerMap.remove(user);
	}

	//Remove effects without removing from HashMap (for use in Iterations)
	public static void removeControlledEffects(Player user)
	{
		ControlledMob ctrlMob = _controllerMap.get(user);
		//LivingEntity controlled = ctrlMob.getMob();
		boolean wasBp = false;
		if(ctrlMob.isBackpack())
		{
			ctrlMob.setNotBackpack();
			wasBp = true;
			//user.sendMessage("set not backpack");
		}
		
		//unmounting a mob calls a VehicleExitEvent, maybe i dont need setNotControlling
		ctrlMob.unMountMob();
		ctrlMob.removeMount();
		ctrlMob.applyVelocity(wasBp);
	}

	public void startTicker()
	{
		new BukkitRunnable() {

			@Override
			public void run() {
				Iterator<Entry<Player, ControlledMob>> iter = _controllerMap.entrySet().iterator();

				while(iter.hasNext())
				{
					Entry<Player, ControlledMob> entry = iter.next();

					moveGrabbedMob(entry.getKey(), entry.getValue());
					if(!entry.getValue().isBackpack())
						drawGrabbedParticle(entry.getValue());
				}
			}

		}.runTaskTimer(MobPlugin.getMobPlugin(), 10, 1);
	}

	public static void moveGrabbedMob(Player grabber, ControlledMob grabbed)
	{
		Location heldLocation;

		heldLocation = calculateHeldLoc(grabber, grabbed.getMob(), grabbed.getDistance());
			//heldLocation = calculateBackLoc(grabber, grabbed.getMob());
			


		//EntityPig nmsPig = ((CraftPig) grabbed.getMount()).getHandle();
		//nmsPig.setLocation(heldLocation.getX(), heldLocation.getY(), heldLocation.getZ(), heldLocation.getYaw(), heldLocation.getPitch());

		//get current 'velocity' and record it
		//just using getVelocity method on mount doesn't work
		Vector currentVelocity = heldLocation.toVector().subtract(grabbed.getMount().getLocation().toVector());
		grabbed.setVelocity(currentVelocity);

		//https://www.spigotmc.org/threads/get-nms-class-from-bukkit-class.285298/
		//https://www.sSilverfishotmc.org/threads/helping-to-teleport-a-packet-entity-with-passenger.358136/
		//use NMS mob because teleporting a bukkit entity with passengers doesnt work
		float pitch = heldLocation.getPitch();
		float yaw = heldLocation.getYaw();

		Pig nmsPig = ((CraftPig) grabbed.getMount()).getHandle();
		nmsPig.moveTo(heldLocation.getX(), heldLocation.getY(), heldLocation.getZ(), yaw, pitch);
	}

	//calculate the location grabbed mobs should be held at
	public static Location calculateHeldLoc(Player grabber, LivingEntity grabbed, double holdingDistance)
	{
		Vector userDirection = grabber.getLocation().getDirection().multiply(holdingDistance);
		Location heldLoc = grabber.getEyeLocation().add(userDirection);

		//not be able to drag them inside blocks
		RayTraceResult rayTrace = grabber.getWorld().rayTraceBlocks(
				grabber.getEyeLocation(), userDirection, holdingDistance + 0.4, FluidCollisionMode.NEVER, true);

		if(rayTrace != null)
		{
			//Vector hitPosition  = rayTrace.getHitPosition();
			// TODO
			//grabber.sendMessage("block=" + rayTrace.getHitBlock() + ",position=" + rayTrace.getHitPosition());
			Vector hitPosition = rayTrace.getHitPosition();
			hitPosition.add(new Vector(0, -(grabbed.getHeight() / 2), 0));

			Vector offset = userDirection.clone().normalize();

			offset.setX(offset.getX() * 0.3);
			offset.setY(offset.getY() * (grabbed.getHeight() / 2));
			offset.setZ(offset.getZ() * 0.3);

			hitPosition.subtract(offset);

			heldLoc.setX(hitPosition.getX());
			heldLoc.setY(hitPosition.getY());
			//heldLoc.add(0, -(grabbed.getHeight() / 2), 0);
			heldLoc.setZ(hitPosition.getZ());
		}
		else
		{
			heldLoc.add(0, -(grabbed.getHeight() / 2), 0);
		}

		heldLoc.setDirection(grabbed.getLocation().getDirection());

		return heldLoc;
	}

	/*
	public static Location calculateBackLoc(Player grabber, LivingEntity grabbed)
	{
		Location loc = grabber.getLocation().clone();
		Vector backwards = grabber.getLocation().getDirection().clone();
		backwards.setY(0).normalize();
		//0.3
		//instance parteertern matching????????????????????
		if(grabbed instanceof Ageable ageable && !ageable.isAdult())
		{
			backwards.multiply(-grabbed.getWidth());
			loc.setY(loc.getY() + 0.75d);
		}
		else
		{
			backwards.multiply(-grabbed.getWidth() + 0.29);
			loc.setY(loc.getY() + 0.7d);
		}
		
		
		
		//backwards.multiply(-grabbed.getWidth() + 0.29);
		//loc.setY(loc.getY() + 0.7d);
		
		
		loc.add(backwards);
		
		
		//after calculating where they'll be held, if it'll block
		//the grabber's view them move them back a little

		Location grabEyeLoc = grabber.getEyeLocation();

		//width from centre of mob (radius but not circle)
		double width = grabbed.getWidth() / 2;
		Location corner1 = loc.clone().add(width, grabbed.getHeight(), width);
		Location corner2 = loc.clone().subtract(width, 0, width);

		if(grabEyeLoc.getX() < corner1.getX() && grabEyeLoc.getX() > corner2.getX())
		{
			//Y check not needed, grabbed only moves on X and Z
			//if(grabEyeLoc.getY() < corner1.getY() && grabEyeLoc.getY() > corner2.getY())
			//{
			if(grabEyeLoc.getZ() < corner1.getZ() && grabEyeLoc.getZ() > corner2.getZ())
			{
				//grabber.sendMessage("inside you");
				//find the corner closest to grabber eye and teleport grabbed out of grabber eye loc

				//compare the X of the corners
				double xDist1 = Math.abs(grabEyeLoc.toVector().subtract(corner1.toVector()).getX());
				double xDist2 = Math.abs(grabEyeLoc.toVector().subtract(corner2.toVector()).getX());
				double xDist;
				if(xDist1 < xDist2)
					xDist = grabEyeLoc.toVector().subtract(corner1.toVector()).getX();
				else
					xDist = grabEyeLoc.toVector().subtract(corner2.toVector()).getX();

				double zDist1 = Math.abs(grabEyeLoc.toVector().subtract(corner1.toVector()).getZ());
				double zDist2 = Math.abs(grabEyeLoc.toVector().subtract(corner2.toVector()).getZ());
				double zDist;
				if(zDist1 < zDist2)
					zDist = grabEyeLoc.toVector().subtract(corner1.toVector()).getZ();
				else
					zDist = grabEyeLoc.toVector().subtract(corner2.toVector()).getZ();

				grabber.sendMessage("x=" + xDist + ",z=" + zDist);
				grabber.sendMessage("yaw=" + grabber.getLocation().getYaw());
				
				
				
			}
			//}
		}

		loc.setDirection(grabber.getLocation().getDirection());

		return loc;
	}
	*/
	
	public static boolean isControlled(LivingEntity controlled)
	{
		Iterator<Entry<Player, ControlledMob>> itel = _controllerMap.entrySet().iterator();
		boolean isControlled = false;

		while(itel.hasNext())
		{
			Entry<Player, ControlledMob> entry = itel.next();

			if(entry.getValue().getMob() == controlled)
			{
				//((Player) controlled).sendMessage("isControlled returned true");
				isControlled = true;
				break;
			}
		}
		return isControlled;
	}

	/**
	 * Basically just if(isControlled(victim)) freeThem();
	 *  But I didn't want to iterate over the HashMap twice (once for if statement, second for freeing)
	 *  so this method.
	 */
	public static void freeIfControlled(LivingEntity controlled)
	{
		Iterator<Entry<Player, ControlledMob>> itel = _controllerMap.entrySet().iterator();
		boolean found = false;

		while(itel.hasNext() && !found)
		{
			Entry<Player, ControlledMob> entry = itel.next();

			if(entry.getValue().getMob() == controlled)
			{
				removeControlledEffects(entry.getKey());
				if(!entry.getValue().isRemoved())
				{
					entry.getValue().setRemoved();
					itel.remove();
				}
				found = true;
			}
		}
	}

	/**
	 * @param grabbed
	 * Remove grabbed effects (dismount and remove pig) without
	 *  removing from iterator.
	 */
	public static void removeEffectsByGrabbed(LivingEntity grabbed)
	{
		Iterator<Entry<Player, ControlledMob>> itel = _controllerMap.entrySet().iterator();
		boolean found = false;

		while(itel.hasNext() && !found)
		{
			Entry<Player, ControlledMob> entry = itel.next();

			if(entry.getValue().getMob() == grabbed)
			{
				//removeControlledEffects(entry.getKey());
				removeControlledEffects(entry.getKey());
				found = true;
			}
		}
	}

	//	@EventHandler
	//	public void onShift(PlayerToggleSneakEvent event)
	//	{
	//		if(_controllerMap.get(event.getPlayer()) != null)
	//		{
	//			setNotControlling(event.getPlayer());
	//		}
	//	}

	//stop from dropping item while grabbing
	@EventHandler
	public void onDropGrabberItem(PlayerDropItemEvent event)
	{
		if(_controllerMap.get(event.getPlayer()) != null)
		{
			ControlledMob mob = _controllerMap.get(event.getPlayer());

			ItemStack dropped = event.getItemDrop().getItemStack().clone();
			dropped.setAmount(1);

			if(dropped.equals(_controllerItem))
			{
				event.setCancelled(true);
				String grabbedName = mob.getMob().getName();
				event.getPlayer().sendMessage(ChatColor.GRAY + "Can't drop this while holding " + grabbedName);
				
				//hack to stop the left-click PlayerInteractEvent from firing
				//and throwing the grabbed mob
				//mob.tossed = true;
			}
		}
	}

	//put grabber item into offhand - backpack mode


	//put the hotbar slot to slot 4 for optimal distance scrolling
	@EventHandler
	public void onGrabberShift(PlayerToggleSneakEvent event)
	{
		if(_controllerMap.get(event.getPlayer()) != null)
		{
			ControlledMob mob = _controllerMap.get(event.getPlayer());
			if(event.isSneaking())
			{
				mob.setLastSlot(event.getPlayer().getInventory().getHeldItemSlot());
				event.getPlayer().getInventory().setHeldItemSlot(4);
			}
			else
			{
				event.getPlayer().getInventory().setHeldItemSlot(mob.getLastSlot());
			}
		}
	}


	public static void drawParticleLine(Location start, Vector direction, double length)
	{
		Vector step = direction.clone().normalize().multiply(0.5);
		Location stepLoc = start.clone();

		for(int i = 1; i < length * 2; i++)
		{
			//NEEDS TESTING
			//step.multiply(i);
			stepLoc.add(step);

			//hit a wall or sth
			//if(stepLoc.getBlock().getBlockData().getMaterial() != Material.AIR)
			//	return;

			start.getWorld().spawnParticle(Particle.SPELL_INSTANT, stepLoc, 1);
		}
	}

	public static void drawGrabbedParticle(ControlledMob grabbed)
	{
		Location loc = grabbed.getMount().getLocation();
		loc.add(0, grabbed.getMount().getHeight(), 0);

		DustOptions colour = new DustOptions(org.bukkit.Color.ORANGE, 2);

		loc.getWorld().spawnParticle(Particle.REDSTONE, loc.getX(), loc.getY(), loc.getZ(), 0, 
				0, 0, 0, 100, colour);
	}

	//adjust holding distance when shift+scroll hotbar
	@EventHandler
	public void onScroll(PlayerItemHeldEvent event)
	{

		if(event.getPlayer().isSneaking() && _controllerMap.get(event.getPlayer()) != null)
		{
			int from = event.getPreviousSlot();
			int to = event.getNewSlot();
			int difference = from - to;

			Player p = event.getPlayer();

			//event.getPlayer().sendMessage("from=" + event.getPreviousSlot() + " to=" + event.getNewSlot() +
			//		"diff=" + difference);

			ControlledMob mob = _controllerMap.get(p);

			ChatColor numColour;

			if(mob.getDistance() + difference <= minDistance)
			{
				mob.setDistance(minDistance);
				//p.sendMessage("§8You can't hold " + mob.getMob().getName() + " any closer");
				numColour = ChatColor.RED;
			}
			else if(mob.getDistance() + difference >= maxDistance)
			{
				mob.setDistance(maxDistance);
				//p.sendMessage("§8You can't hold " + mob.getMob().getName() + " any further");
				numColour = ChatColor.RED;
			}
			else
			{
				mob.setDistance(mob.getDistance() + difference);
				numColour = ChatColor.GRAY;
			}

			//cool effect
			String sub = ChatColor.DARK_GRAY + "Distance: " + numColour + round(mob.getDistance(), 2) + 
					" blocks";

			p.sendTitle(" ", sub, 1, 30, 1);

			event.setCancelled(true);
		}
	}

	//	public static HashMap<Player, ControlledMob> getControllerMap()
	//	{
	//		return _controllerMap;
	//	}

	public static boolean isAllowedBp()
	{
		return allowedBp;
	}

	public static void clearControllerMap()
	{
		Iterator<Entry<Player, ControlledMob>> itel = _controllerMap.entrySet().iterator();

		while(itel.hasNext())
		{
			Entry<Player, ControlledMob> entry = itel.next();
			removeControlledEffects(entry.getKey());
			itel.remove();
		}
	}

	public static HashMap<Player, ControlledMob> getMap()
	{
		return _controllerMap;
	}

	@EventHandler
	public void vehicleExit(VehicleExitEvent event)
	{
		//Bukkit.broadcastMessage("vvehicle exit triggerd");

		LivingEntity livent = event.getExited();

		if(!livent.isInWater())
		{
			//Bukkit.broadcastMessage("dsimounted not in water");
			freeIfControlled(livent);
		}
		else
		{
			if(livent instanceof Player)
			{
				Player p = (Player) livent;
				if(p.isSneaking())
				{
					//Bukkit.broadcastMessage("dismounted sneaking in water");
					freeIfControlled(p);

					//avoid cancelling event
					return;
				}
			}
			//cancel if they're dismounting coz of entering water,
			//but not if they're sneaking (voluntarily leaving)
			//Bukkit.broadcastMessage("cancelled event");
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onControlledDeath(EntityDeathEvent event)
	{
		if(event.getEntity() instanceof LivingEntity)
		{
			freeIfControlled(event.getEntity());
		}
	}

	@EventHandler
	public void onGrabberDeath(PlayerDeathEvent event)
	{
		if(_controllerMap.get(event.getEntity()) != null)
		{
			setNotControlling(event.getEntity());
		}
	}

	@EventHandler
	public void onGrabbedExplode(ExplosionPrimeEvent event)
	{
		if(event.getEntity() instanceof LivingEntity)
		{
			LivingEntity livent = (LivingEntity) event.getEntity();
			freeIfControlled(livent);
		}
	}

	//Handle logging off grabbers
	@EventHandler
	public void handleDisconnect(PlayerQuitEvent event)
	{
		if(_controllerMap.get(event.getPlayer()) != null)
		{
			//setNotControlling(event.getPlayer());
			ControlledMob mob = _controllerMap.get(event.getPlayer());
			mob.unMountMob();
			mob.removeMount();
			_controllerMap.remove(event.getPlayer());
		}

		freeIfControlled(event.getPlayer());
		
		//grabbing player may also be grabbed
		//problem if they're in water, since vehicleExit does something
		//so just check for water
//		if(event.getPlayer().isInWater())
//		{
//			freeIfControlled(event.getPlayer());
//		}
//		else
//		{
//			//only removes if controlled
//			removeEffectsByGrabbed(event.getPlayer());
//		}
	}


	//https://stackoverflow.com/questions/2808535/round-a-double-to-2-decimal-places
	//https://stackoverflow.com/a/2808648
	//taken on 27/06/2021
	public static double round(double value, int places) {
		if (places < 0) throw new IllegalArgumentException();

		BigDecimal bd = BigDecimal.valueOf(value);
		bd = bd.setScale(places, RoundingMode.HALF_UP);
		return bd.doubleValue();
	}
}
