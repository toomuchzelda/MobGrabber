package me.toomuchzelda.mobplugin;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pig;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.util.Vector;

/**
 * @author toomuchzelda
 *
 */
public class ControlledMob implements Listener
{
	private LivingEntity _controlled;
	private Pig _mount;
	
	//just record to apply to mounted mob when releasing
	//(throwing the mob kind of effect)
	private Vector velocity;

	/**
	 * @param controlled The LivingEntity to be grabbed
	 */
	public ControlledMob(LivingEntity controlled)
	{
		_controlled = controlled;
		//if(controlled instanceof Player)
		//{
		_mount = (Pig) _controlled.getWorld().spawnEntity(_controlled.getLocation(), EntityType.PIG);
		//_mount.setInvisible(true);
		_mount.setBaby();
		_mount.setAgeLock(true);
		_mount.setGravity(false);
		_mount.setAI(false);
		_mount.setSilent(true);
		_mount.setCollidable(false);
		//		}
		//		else
		//		{
		//_mount = null;

		//			_mount = (Pig) _controlled.getWorld().spawnEntity(_controlled.getLocation(), EntityType.PIG);
		//			_mount.setInvisible(true);
		//			_mount.setBaby();
		//			_mount.setAgeLock(true);
		//			_mount.setGravity(false);
		//			_mount.setAI(false);
		//			_mount.setSilent(true);
		//			_mount.setCollidable(false);
		//		//}


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
		//		if(!_isPlayer)
		//			throw new IllegalStateException("This ControlledMob instance is not a Player and "
		//					+ "doesn't have a mount");
		//		
		return _mount;
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
		_controlled.setVelocity(velocity);
	}
	
	@Override
	public String toString()
	{
		String s = "(ControlledMob: mob=" + _controlled.getName();//+ ",isPlayer=" + _isPlayer;
		//if(_isPlayer)
		//{
		s += ",mount=" + _mount.getName();
		//}
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
}
