package me.toomuchzelda.mobplugin;

import java.util.ArrayList;
import java.util.Collection;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import net.md_5.bungee.api.ChatColor;

/**
 * @author toomuchzelda
 *
 */
public class ControlledMob implements Listener
{
	private Player _grabber;
	private LivingEntity _controlled;
	private Pig _mount;

	//just record to apply to mounted mob when releasing
	//(throwing the mob kind of effect)
	private Vector velocity;

	//distance to hold the mob from the player
	private double holdingDistance;

	//last held item slot for distance scrolling
	private int lastSlot;

	//rubbish to avoid ConcurrentModificationExceptions across multiple different events that remove
	//from the hashmap
	private boolean removed = false;

	private boolean isBackpack;

	//band aid to avoid throwing the mob when just dropping the item.
	public boolean tossed = false;

	/**
	 * @param controlled The LivingEntity to be grabbed
	 */
	public ControlledMob(Player grabber, LivingEntity controlled, double distance, int slot)
	{
		_controlled = controlled;
		_grabber = grabber;

		_mount = (Pig) _controlled.getWorld().spawnEntity(_controlled.getLocation(), EntityType.PIG);
		_mount.setInvisible(true);
		_mount.setBaby();
		_mount.setAgeLock(true);
		_mount.setGravity(false);
		_mount.setAI(false);
		_mount.setSilent(true);
		_mount.setCollidable(false);
		_mount.setCustomName("MobGrabberPig" + _mount.getEntityId());
		_mount.setCustomNameVisible(false);

		this.holdingDistance = distance;
		this.lastSlot = slot;
		this.isBackpack = false;

		MobPlugin.getMobPlugin().getServer().getPluginManager().registerEvents(this, MobPlugin.getMobPlugin());
	}

	/**
	 * If the controlled mob is a Player, put them on their Pig.
	 * <br>
	 * Usually used for handling desyncs/dismounts
	 */
	public void mountPlayer()
	{
		//		if(_isPlayer)
		//		{
		_mount.addPassenger(_controlled);
		//		}
		//		else
		//		{
		//			throw new IllegalStateException("This ControlledMob is not a Player");
		//		}
	}

	public boolean unMountMob()
	{
		boolean bool = false;
		if(_mount.getPassengers().size() > 0 && _mount.getPassengers().get(0).equals(_controlled))
		{
			bool = _mount.removePassenger(_controlled);
			
			//avoid falling through floors and reset to grabbed facing position (not mount pig)
			Location toTele = _mount.getLocation().clone();
			Block block = toTele.getBlock();
			if(!block.isPassable())
			{
				Collection<BoundingBox> list = block.getCollisionShape().getBoundingBoxes();
				double highest = 0;
				for(BoundingBox box : list)
				{
					if(box.getMaxY() > highest)
						highest = box.getMaxY();
				}
				toTele.add(0, highest, 0);
			}
			toTele.setDirection(_controlled.getLocation().getDirection());

			_controlled.teleport(toTele);
		}
		return bool;
	}

	public void removeMount()
	{
		if(_mount.isValid())
		{
			_mount.remove();
		}
		HandlerList.unregisterAll(this);
	}

	/*public void sendMountPackets()
	{
		if(_isPlayer)
		{
			PacketContainer passengerPacket = new PacketContainer(PacketType.Play.Server.MOUNT);

			passengerPacket.getIntegers().write(0, _mount.getEntityId());
			int[] passengersField = new int[1];
			passengersField[0] = _controlled.getEntityId();
			passengerPacket.getIntegerArrays().write(0, passengersField);

			try
			{
				for(Player everyone : Bukkit.getOnlinePlayers())
				{
					ProtocolLibrary.getProtocolManager().sendServerPacket(everyone, passengerPacket);
				}
			}
			catch (InvocationTargetException e)
			{
				e.printStackTrace();
				Bukkit.broadcastMessage("packet sending failed");
			}
		}
		else
		{
			throw new IllegalStateException("This ControlledMob instance is not a Player.");
		}
	}*/


	/**
	 * @return The LivingEntity being grabbed
	 */
	public LivingEntity getMob()
	{
		return _controlled;
	}

	public Pig getMount()
	{
		return _mount;
	}
	
	public Player getGrabber()
	{
		return _grabber;
	}

	public double getDistance()
	{
		return holdingDistance;
	}

	public void setDistance(double distance)
	{
		this.holdingDistance = distance;
	}

	public int getLastSlot()
	{
		return lastSlot;
	}


	public void setLastSlot(int lastSlot)
	{
		this.lastSlot = lastSlot;
	}

	public void setRemoved()
	{
		removed = true;
	}

	public boolean isRemoved()
	{
		return removed;
	}

	public Vector getVelocity()
	{
		return velocity;
	}

	public void setVelocity(Vector velocity)
	{
		this.velocity = velocity;
	}

	//apply the velocity, used when releasing
	public void applyVelocity()
	{
		if(velocity != null)
			_controlled.setVelocity(velocity);
	}

	public boolean isBackpack()
	{
		return isBackpack;
	}


	public void setBackpack()
	{
		this.isBackpack = true;
		this.putOnHead();
	}

	public void setNotBackpack()
	{
		this.isBackpack = false;
		this.takeOffHead();
	}

	//put grabbed onto grabber's head
	private void putOnHead()
	{
		if(_grabber.addPassenger(_mount))
		{
			
		}
		else
		{
			_grabber.sendMessage(ChatColor.RED + "Couldn't put " + _controlled.getName()
					+ " on head.");
			this.isBackpack = false;
		}
		/*
		if(_mount.getPassengers().size() > 0 && _mount.getPassengers().get(0) == _controlled)
		{
			if(!_mount.removePassenger(_controlled))
			{
				_grabber.sendMessage(ChatColor.RED + "Something went wrong! Couln't take grabbed off mount pig");
				this.isBackpack = false;
			}
			else
			{
				if(!_grabber.addPassenger(_controlled))
				{
					_grabber.sendMessage(ChatColor.RED + "Something went wrong! "
							+ "Couldn't put " + _controlled.getName() + " on your head");
					this.isBackpack = false;
					_mount.addPassenger(_controlled);
				}
				else
				{
					//put them somewhere very high where realistically can't be found
					Location loc = _mount.getLocation();
					loc.setY(500);
					_mount.teleport(loc);
				}
			}
		}
		*/
	}

	private void takeOffHead()
	{
		if(_grabber.getPassengers().contains(_mount))
		{
			if(_grabber.removePassenger(_mount))
			{
				
			}
			else
			{
				_grabber.sendMessage(ChatColor.RED + "Couldn't take " + _controlled.getName() + " off your"
						+ "head");
			}
		}
		/*
		if(_grabber.getPassengers().size() > 0 && _grabber.getPassengers().contains(_controlled))
		{
			if(!_grabber.removePassenger(_controlled))
			{
				_grabber.sendMessage(ChatColor.RED + " Something went wrong! Couldn't take off head.");
				this.isBackpack = true;
			}
			else
			{
				Location loc = MobController.calculateHeldLoc(_grabber, _controlled, holdingDistance);
				_mount.teleport(loc);
				if(!_mount.addPassenger(_controlled))
				{
					_grabber.sendMessage(ChatColor.RED + " Something went wrong! Couldn't put back onto pig");
					this.isBackpack = true;
				}
				else
				{
					_mount.addPassenger(_controlled);
				}
			}
		}
		*/
	}

	@Override
	public String toString()
	{
		String s = "(ControlledMob: mob=" + _controlled.getName();
		s += ",mount=" + _mount.getUniqueId().toString();
		s += ",grabber=" + _grabber.getName();
		s += ')';

		return s;
	}

	//cancel all damage done to the mount pig and suffocation dmg to grabbed mob
	@EventHandler
	public void onDamage(EntityDamageEvent event)
	{
		if(event.getEntity() == _mount || 
				(event.getEntity() == _controlled && event.getCause() == DamageCause.SUFFOCATION))
			event.setCancelled(true);
	}

	@EventHandler
	public void onMountDeath(EntityDeathEvent event)
	{
		if(event.getEntity() == _mount)
		{
			_grabber.sendMessage(ChatColor.RED + "Mount pig died. Something went wrong!");
		}
	}

	//turning backpack mode on/off
	@EventHandler
	public void onHandSwap(PlayerSwapHandItemsEvent event)
	{
		if(MobController.isAllowedBp())
		{
			if(event.getMainHandItem().equals(MobController._controllerItem) && this.isBackpack())
			{
				this.setNotBackpack();
				//_grabber.sendMessage("setnotBP");
			}
			else if(event.getOffHandItem().equals(MobController._controllerItem) && !this.isBackpack())
			{
				this.setBackpack();
				//_grabber.sendMessage("setBP");
			}
		}
	}

	//* 		Rubbish 		*//

	/*
	public static void setupTeam()
	{
		Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
		Team team = sb.getTeam("bpTeam");

		//ensure clean on every reload
		if(team != null)
		{
			team.unregister();
			Bukkit.getLogger().info("Unregistered old bpTeam");
		}
		team = sb.registerNewTeam("bpTeam");
		team.setOption(Option.COLLISION_RULE, OptionStatus.NEVER);
		team.setCanSeeFriendlyInvisibles(false);
		//team.setColor(org.bukkit.ChatColor.LIGHT_PURPLE);

		bpTeam = team;
		Bukkit.getLogger().info("Created bukkit bp team");

	}
	 */


	/*
	//tell the grabber not to collide with other entities
	//either send a packet 'modifying' their current team
	//or 'joining' a dedicated team to do this
	//only do on packet level to not mess with players/other plugins' actual teams
	private void sendDontCollidePacket()
	{
		Team team = _grabber.getScoreboard().getEntryTeam(_grabber.getName());

		if(team != null)
		{
			//make team update packet with collision off and send to them
			PacketContainer newTeamPacket = new PacketContainer(PacketType.Play.Server.SCOREBOARD_TEAM);

			//team name
			String name = team.getName();

			//_grabber.sendMessage("team name=" + name);

			newTeamPacket.getStrings().write(0, name);

			//update scoreboard mode
			newTeamPacket.getIntegers().write(0, 2);

			//player collection. not needed in update team mode
			/*Collection<String> players = new ArrayList<String>();
			players.add(_grabber.getName());
			players.add(_mount.getUniqueId().toString());
			//_grabber.sendMessage("UUID To string: " + _mount.getUniqueId().toString());

			//may not be necessary, they won't get pushed while on
			//	mount
			//String ctrld = _controlled instanceof Player ? _controlled.getName() : 
			//	_controlled.getUniqueId().toString();
			//players.add(ctrld);

			//newTeamPacket.getModifier().write(2, players);

			PlayerTeam nmsTeam = null;

			try
			{
				Field teamField = Class.forName("org.bukkit.craftbukkit.v1_17_R1.scoreboard.CraftTeam").getDeclaredField("team");
	            teamField.setAccessible(true);

	            nmsTeam = (PlayerTeam) teamField.get(team);

//	            _grabber.sendMessage("nms name:" + nmsTeam.getName());
//				_grabber.sendMessage("Collisions:" + nmsTeam.getCollisionRule().toString());
//				_grabber.sendMessage("type: " + nmsTeam.getClass().getCanonicalName());

//				if(bukkitScoreboard != null)
//				{
//					Field boardField = Class.forName("org.bukkit.craftbukkit.v1_17_R1.scoreboard.CraftScoreboard")
//							.getDeclaredField("board");
//		            boardField.setAccessible(true);
//		            
//		            nmsScoreboard = (net.minecraft.world.scores.Scoreboard) boardField.get(bukkitScoreboard);
//		            
////		            Collection<String> s = nmsScoreboard.getTeamNames();
////		            for(String sname : s)
////		            {
////		            	_grabber.sendMessage("team" + sname);
////		            }
//				}
//				else
//				{
//					_grabber.sendMessage("bukkit scoreboard null");
//				}

				//create team options field of packet
				ClientboundSetPlayerTeamPacket.Parameters packetParams = new ClientboundSetPlayerTeamPacket.Parameters(nmsTeam);
				//_grabber.sendMessage("created params");

				//set collision to never
				//Field colParam = packetParams.getClass().getDeclaredField("collisionRule");
				Class<?>[] paramClassArr = ClientboundSetPlayerTeamPacket.class.getDeclaredClasses();

//				Field[] fields = paramClassArr[0].getDeclaredFields();
//				for(Field fd : fields)
//				{
//					fd.setAccessible(true);
//					_grabber.sendMessage(fd.getName());
//				}

				//field still obfuscated? at runtime anyway at least
				Field colParam = paramClassArr[0].getDeclaredField("e");
				colParam.setAccessible(true);
				colParam.set(packetParams, "never");
				//_grabber.sendMessage("reflected and set coll to never");

				//create optional object
				Optional<Parameters> option = Optional.of(packetParams);

				//put options params into packet
				newTeamPacket.getModifier().write(3, option);

				//send packet
				ProtocolLibrary.getProtocolManager().sendServerPacket(_grabber, newTeamPacket);
				//_grabber.sendMessage("sent packet");

			}
			catch(Exception e)
			{
				e.printStackTrace();
				_grabber.sendMessage(ChatColor.GRAY + "Failed to create collision off for existing team packet");
				return;
			}
		}
		else
		{
			//make new team packet with collision off and send it to them
			PacketContainer newTeamPacket = new PacketContainer(PacketType.Play.Server.SCOREBOARD_TEAM);
			String name = bpTeam.getName();

			newTeamPacket.getStrings().write(0, name);

			//add to team mode
			newTeamPacket.getIntegers().write(0, 3);

			//player collection
			Collection<String> players = new ArrayList<String>();
			players.add(_grabber.getName());
			players.add(_mount.getUniqueId().toString());

			newTeamPacket.getModifier().write(2, players);

			try
			{
				//create packet params with existing nmsBp team
				//ClientboundSetPlayerTeamPacket.Parameters packetParams = new ClientboundSetPlayerTeamPacket.Parameters(nmsBpTeam);

				//dont need to modify the collision rule since its already set in nmsBpTeam

				//Optional<Parameters> optional = Optional.of(packetParams);

				//packet JOIN mode doesnt need options
				Optional<Parameters> noOption = Optional.empty();

				newTeamPacket.getModifier().write(3, noOption);

				ProtocolLibrary.getProtocolManager().sendServerPacket(_grabber, newTeamPacket);
				//_grabber.sendMessage("sent no team packet");
			}
			catch(Exception e)
			{
				e.printStackTrace();
				_grabber.sendMessage(ChatColor.GRAY + "Could not create new fake team packet");
				return;
			}
		}
	}

	//reset the collision/teams on the client to whatever it was before
	//backpacking
	private void sendNormalTeamPacket()
	{
		Team team = _grabber.getScoreboard().getEntryTeam(_grabber.getName());

		//make a 'change' so the server will update the client which will also
		//reset their collision state
		if(team != null)
		{
			team.setSuffix(team.getSuffix());
		}
		else
		{
			bpTeam.addEntry(_grabber.getName());
			bpTeam.removeEntry(_grabber.getName());
		}
	}	*/
}
