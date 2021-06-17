package me.toomuchzelda.mobplugin;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import net.minecraft.world.entity.animal.Pig;

/**
 * @author toomuchzelda
 * 
 *
 */
public class MobController implements Listener
{
	public ItemStack _controllerItem;
	//						user,    target
	private static HashMap<Player, ControlledMob> _controllerMap = new HashMap<>();
	
	//private static final PotionEffect _noJump = new PotionEffect(PotionEffectType.JUMP, 99999, 150, false, false, false);
	public static final double HOLDING_DISTANCE = 3.5d;
	
	//for some bad behaviour that doesnt appear in PaperMC
	public static boolean isPaperMC = false;
	
	public MobController(MobPlugin plugin)
	{
		this.createControllerItem();
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
		//create the mobgrabber item
		ItemStack item = new ItemStack(Material.SHULKER_SHELL);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName("Mob Controller");
		item.setItemMeta(meta);
		_controllerItem = item;
	}
	
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
				if(item.equals(_controllerItem) && _controllerMap.get(event.getPlayer()) == null)
				{
					//perform a raytrace to see if they're targetting an entity or not
					World world = event.getPlayer().getWorld();
					Player p = event.getPlayer();
					
					//uuuuuuhhhhhh
					Predicate<Entity> notUserOrSpectator = new Predicate<Entity>()
					{

						@Override
						public boolean test(Entity e) {
							boolean bool = false;
							if(e.getEntityId() != p.getEntityId())
							{
								bool = true;
								if(e instanceof Player)
								{
									Player rayPlayer = (Player) e;
									if(rayPlayer.getGameMode().equals(GameMode.SPECTATOR))
									{
										bool = false;
									}
								}
							}
							return bool;
						}
					};
					
					RayTraceResult result = world.rayTrace(p.getEyeLocation(), p.getLocation().getDirection()
							, 30.0, FluidCollisionMode.NEVER, true, 0, notUserOrSpectator);
					
					drawParticleLine(p.getEyeLocation(), p.getLocation().getDirection(), 30);
					
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
								setControlling(p, livent);
								
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
			}
		}
	}
	
	public static void setControlling(Player user, LivingEntity target)
	{
		if(_controllerMap.get(user) == null)
		{
			ControlledMob ctrlMob;

			if(!isPaperMC)
			{
				target.teleport(calculateHeldLoc(user, target, HOLDING_DISTANCE));
			}

			ctrlMob = new ControlledMob(target);

			ctrlMob.mountPlayer();

			_controllerMap.put(user, ctrlMob);
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
			
		ctrlMob.unMountMob();
		ctrlMob.removeMount();
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
				}
			}

		}.runTaskTimer(MobPlugin.getMobPlugin(), 10, 1);
	}
	
	public static void moveGrabbedMob(Player grabber, ControlledMob grabbed)
	{
		Location heldLocation = calculateHeldLoc(grabber, grabbed.getMob(), HOLDING_DISTANCE);

		//if(grabbed.isPlayer())
		//{
		//https://www.spigotmc.org/threads/get-nms-class-from-bukkit-class.285298/
		//https://www.sSilverfishotmc.org/threads/helping-to-teleport-a-packet-entity-with-passenger.358136/
		//use NMS mob because teleporting a bukkit entity with passengers doesnt work

		//EntityPig nmsPig = ((CraftPig) grabbed.getMount()).getHandle();
		//nmsPig.setLocation(heldLocation.getX(), heldLocation.getY(), heldLocation.getZ(), heldLocation.getYaw(), heldLocation.getPitch());
		Pig nmsPig = ((CraftPig) grabbed.getMount()).getHandle();
		nmsPig.moveTo(heldLocation.getX(), heldLocation.getY(), heldLocation.getZ());
		
		//grabbed.getMount().teleport(heldLocation);
		//		}
		//		else
//		{
//			grabbed.getMob().teleport(heldLocation);
//		}

	}
	
	//calculate the location grabbed mobs should be held at
	public static Location calculateHeldLoc(Player grabber, LivingEntity grabbed, double holdingDistance)
	{
		LivingEntity controlled = grabbed;
		
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
			hitPosition.add(new Vector(0, -(controlled.getHeight() / 2), 0));

			Vector offset = userDirection.clone().normalize();
			
			offset.setX(offset.getX() * 0.3);
			offset.setY(offset.getY() * (controlled.getHeight() / 2));
			offset.setZ(offset.getZ() * 0.3);

			hitPosition.subtract(offset);

			heldLoc.setX(hitPosition.getX());
			heldLoc.setY(hitPosition.getY());
			//heldLoc.add(0, -(controlled.getHeight() / 2), 0);
			heldLoc.setZ(hitPosition.getZ());
		}
		else
		{
			heldLoc.add(0, -(controlled.getHeight() / 2), 0);
			heldLoc.setDirection(controlled.getLocation().getDirection());
		}

		return heldLoc;
	}
	
	@EventHandler
	public void onControlledPlayerMove(PlayerMoveEvent event)
	{
		if(isControlled(event.getPlayer()))
		{
			Vector from = event.getFrom().toVector();
			Vector to = event.getTo().toVector();
			Vector difference = to.subtract(from);
			if(difference.equals(new Vector().zero()))
				return;
			
			event.setCancelled(true);
		}
	}
	
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
				//removeControlledEffects(entry.getKey());
				entry.getValue().unMountMob();
				entry.getValue().removeMount();
				itel.remove();
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
				entry.getValue().unMountMob();
				entry.getValue().removeMount();
				found = true;
			}
		}
	}
	
	@EventHandler
	public void onShift(PlayerToggleSneakEvent event)
	{
		if(_controllerMap.get(event.getPlayer()) != null)
		{
			setNotControlling(event.getPlayer());
		}
	}
	
	public static void drawParticleLine(Location start, Vector direction, int length)
	{
		Vector step = direction.clone().normalize();
		Location stepLoc = start.clone();
		
		for(int i = 1; i < length * 2; i++)
		{
			//NEEDS TESTING
			//step.multiply(i);
			stepLoc.add(step);
			
			if(stepLoc.getBlock().getBlockData().getMaterial() != Material.AIR)
				return;
			
			start.getWorld().spawnParticle(Particle.SPELL_INSTANT, stepLoc, 1);
		}
	}
	
//	public static HashMap<Player, ControlledMob> getControllerMap()
//	{
//		return _controllerMap;
//	}
	
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
	
	/*@EventHandler
	public void onDismount(EntityDismountEvent event)
	{
		if(event.getEntity() instanceof LivingEntity)
		{
			LivingEntity livent = (LivingEntity) event.getEntity();
			if(isControlled(livent))
			{
				//just setCancelling doesn't work for some reason. manually dismount and re-mount
				//event.setCancelled(true);
				//event.getDismounted().removePassenger(livent);
				event.getEntity().sendMessage("dismount triggered");
				event.getDismounted().addPassenger(livent);
				
				if(livent instanceof Player)
				{
					Player passenger = (Player) livent;
					
					PacketContainer passengerPacket = new PacketContainer(PacketType.Play.Server.MOUNT);
					
					passengerPacket.getIntegers().write(0, event.getDismounted().getEntityId());
					int[] passengersField = new int[1];
					//passengersField[0] = 1;
					passengersField[0] = passenger.getEntityId();
					
					passengerPacket.getIntegerArrays().write(0, passengersField);
					
					try
					{
						ProtocolLibrary.getProtocolManager().sendServerPacket(passenger, passengerPacket);
					}
					catch (InvocationTargetException e)
					{
						e.printStackTrace();
						Bukkit.broadcastMessage("packet sending failed");
					}
					passenger.sendMessage("packet sended");
				}
			}
		}
	}	*/
	
	@EventHandler
	public void vehicleExit(VehicleExitEvent event)
	{
		Bukkit.broadcastMessage("vvehicle exit triggerd");
		
		LivingEntity livent = event.getExited();
		
		if(!livent.getLocation().getBlock().getBlockData().getMaterial().equals(Material.WATER))
		{
			Bukkit.broadcastMessage("dsimounted not in water");
			freeIfControlled(livent);
		}
		else
		{
			if(livent instanceof Player)
			{
				Player p = (Player) livent;
				if(p.isSneaking())
				{
					Bukkit.broadcastMessage("dismounted sneaking in water");
					freeIfControlled(p);
					
					//avoid cancelling event
					return;
				}
			}
			//cancel if they're dismounting coz of entering water,
			//but not if they're sneaking (voluntarily leaving)
			Bukkit.broadcastMessage("cancelled event");
			event.setCancelled(true);
		}
	}
	
	//Only handles non-players
	/*
	@EventHandler
	public void onEntityDismount(EntityDismountEvent event)
	{
		Bukkit.broadcastMessage("entity dismount triggered");
		if(event.getEntity() instanceof LivingEntity)
		{
			LivingEntity livent = (LivingEntity) event.getEntity();
			if(isControlled(livent))
			{
				event.setCancelled(true);
			}
		}
	}	*/
	
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
		
		//grabbing player may also be grabbed
		if(isControlled(event.getPlayer()))
		{
			//Avoid concurrent modification exceptions by not iterator removing
			removeEffectsByGrabbed(event.getPlayer());
		}
		
	}
}
