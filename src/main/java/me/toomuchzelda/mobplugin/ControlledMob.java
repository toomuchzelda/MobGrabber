package me.toomuchzelda.mobplugin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;
import org.bukkit.util.Vector;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;

import net.md_5.bungee.api.ChatColor;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.Parameters;
import net.minecraft.world.scores.PlayerTeam;

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
	
	//ratchet and clank
	private boolean isBackpack;
	
	//hack to avoid throwing the mob when just dropping the item.
	public boolean tossed = false;
	
	//for putting player who's not in team into fake team with packets
	//for disabling collision only for them
	private static Team bpTeam;
	private static PlayerTeam nmsBpTeam;

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

		this.holdingDistance = distance;
		this.lastSlot = slot;
		this.isBackpack = false;
		
		MobPlugin.getMobPlugin().getServer().getPluginManager().registerEvents(this, MobPlugin.getMobPlugin());
	}

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
		
		nmsBpTeam = getNMSTeam(bpTeam);
	}
	
	private static PlayerTeam getNMSTeam(Team bukkitTeam)
	{
		PlayerTeam nmsTeam = null;
		try
		{
			Field teamField = Class.forName("org.bukkit.craftbukkit.v1_17_R1.scoreboard.CraftTeam").getDeclaredField("team");
	        teamField.setAccessible(true);
	        nmsTeam = (PlayerTeam) teamField.get(bukkitTeam);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			Bukkit.getLogger().warning("Couldn't set up fake NMS team");
		}
		
		Bukkit.getLogger().info("Created nms bp team");
		
		return nmsTeam;
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

	public void unMountMob()
	{
		if(_mount.getPassengers().size() > 0 &&_mount.getPassengers().get(0).equals(_controlled))
		{
			_mount.removePassenger(_controlled);
			
			//avoid falling through floors and reset to grabbed facing position (not mount pig)
			Location toTele = _mount.getLocation().clone().add(0, 0.5, 0);
			toTele.setDirection(_controlled.getLocation().getDirection());
			
			_controlled.teleport(toTele);
		}
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
		this.sendDontCollidePacket();
	}
	
	public void setNotBackpack()
	{
		this.isBackpack = false;
	}

	//tell the grabber not to collide with other entities
	//either send a packet 'modifying' their current team
	//or 'creating' a new team to do this
	//only do on packet level to not mess with players/other plugins' actual teams
	public void sendDontCollidePacket()
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
			
			//player collection. may not be needed in update team mode
			/*Collection<String> players = new ArrayList<String>();
			players.add(_grabber.getName());
			players.add(_mount.getUniqueId().toString());*/
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
				_grabber.sendMessage("sent no team packet");
			}
			catch(Exception e)
			{
				e.printStackTrace();
				_grabber.sendMessage(ChatColor.GRAY + "Could not create new fake team packet");
				return;
			}
			
			
		}
	}
	
	@Override
	public String toString()
	{
		String s = "(ControlledMob: mob=" + _controlled.getName();
		s += ",mount=" + _mount.getName();
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
	
	//turning backpack mode on/off
	@EventHandler
	public void onHandSwap(PlayerSwapHandItemsEvent event)
	{
		if(event.getMainHandItem().equals(MobController._controllerItem) && this.isBackpack())
		{
			this.setNotBackpack();
			_grabber.sendMessage("setnotBP");
		}
		else if(event.getOffHandItem().equals(MobController._controllerItem) && !this.isBackpack())
		{
			this.setBackpack();
			_grabber.sendMessage("setBP");
		}
	}
}
