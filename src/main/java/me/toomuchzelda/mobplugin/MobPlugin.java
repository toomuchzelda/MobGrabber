package me.toomuchzelda.mobplugin;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import net.md_5.bungee.api.ChatColor;

/**
 * @author toomuchzelda
 *
 */
public final class MobPlugin extends JavaPlugin
{
	private static MobPlugin _mobPlugin;
	MobController _mobController;
	FileConfiguration config = this.getConfig();

	public static final String minDistConfig = "minimumDistance";
	public static final String maxDistConfig = "maximumDistance";
	public static final String craftableConfig = "craftable";
	public static final String cmdOpsOnlyConfig = "give-command-ops-only";
	public static final String allowBpConfig = "allow-backpack";
	public static final String allowGrabPlayersConfig = "allow-grabbing-players";
	public static final String forcePlayerGrabConfig = "ignore-player-grab-consent";
	public static final String disablePlayerDismount = "disable-player-dismount";
	public static NamespacedKey consentKey;
	
	@Override
	public void onEnable()
	{
		_mobPlugin = this;
		
		consentKey = new NamespacedKey(getMobPlugin(), "mob-grabber-consent");

		config.addDefault(minDistConfig, 1.2d);
		config.addDefault(maxDistConfig, 30);
		config.addDefault(craftableConfig, true);
		config.addDefault(cmdOpsOnlyConfig, false);
		config.addDefault(allowBpConfig, true);
		config.addDefault(allowGrabPlayersConfig, true);
		config.addDefault(forcePlayerGrabConfig, false);
		config.addDefault(disablePlayerDismount, false);

		config.options().copyDefaults(true);
		saveConfig();
		
		killLeftovers();

		_mobController = new MobController(this, config);

		_mobController.startTicker();
	}

	@Override
	public void onDisable()
	{
		MobController.clearControllerMap();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
	{
		if(cmd.getName().equalsIgnoreCase("mobgrabber"))
		{
			if(args.length == 0)
			{
				sender.sendMessage(ChatColor.YELLOW + "Type /mbc help for info on how to use");
			}
			else if(args.length > 0)
			{
				if(args[0].equals("give"))
				{
					if(sender.hasPermission("mobgrabber.give"))
					{
						if(sender instanceof Player)
						{
							Player p = (Player) sender;
							if((config.getBoolean("give-command-ops-only") && p.isOp() ||
									!config.getBoolean("give-command-ops-only")))
							{
								p.getInventory().addItem(MobController._controllerItem);
								sender.sendMessage(ChatColor.BLUE + "Given mob grabber");
							}
							else
							{
								p.sendMessage(ChatColor.RED + "Only operators can use this command");
							}
						}
						else
						{
							sender.sendMessage(ChatColor.RED + "Can't receive the item if you're not a player!");
						}
					}
					else
					{
						sender.sendMessage(ChatColor.RED + "You don't have permission");
					}
				}
				else if(args[0].equals("debug"))
				{
					sender.sendMessage(MobController.getMap().toString());
					//sender.sendMessage("Running PaperMC: " + MobController.isPaperMC);
				}
				else if(args[0].equals("consent"))
				{
					if(sender instanceof Player)
					{
						Player p = (Player) sender;
						PersistentDataContainer data = p.getPersistentDataContainer();
						//1 for yes, 0 for no
						if(!data.has(consentKey, PersistentDataType.INTEGER))
						{
							data.set(consentKey, PersistentDataType.INTEGER, 0);
							p.sendMessage(ChatColor.BLUE + "Disabling grabbed by other players");
						}
						else
						{
							int allowed = data.get(consentKey, PersistentDataType.INTEGER);
							if(allowed == 0)
							{
								data.set(consentKey, PersistentDataType.INTEGER, 1);
								p.sendMessage(ChatColor.BLUE + "Allowing grabbed by other players");
							}
							else
							{
								data.set(consentKey, PersistentDataType.INTEGER, 0);
								p.sendMessage(ChatColor.BLUE + "Disabling grabbed by other players");
							}
						}
						if(MobController.forcePlayerGrabs)
							sender.sendMessage(ChatColor.RED + "Force grabs enabled in config!");
					}
					else
					{
						sender.sendMessage(ChatColor.RED + "You're not a Player");
					}
				}
				else if(args[0].equals("help"))
				{
					sender.sendMessage("===Mob Grabber===");
					sender.sendMessage("/mbc give - get the Mob Grabber item");
					sender.sendMessage("/mbc consent - toggle allowing other players to grab you. Can be forced"
							+ " on/off in plugins/MobPlugin/config.yml");
					sender.sendMessage("/mbc help - view this page");
					sender.sendMessage("Point at a mob and right click the item to pick them up!\n"
							+ "To drop them, hold the Mob Grabber and right click.\n"
							+ "To move them closer/further, Sneak and scroll up/down your hotbar\n"
							+ "Carry a mob on your head by holding the Grabber item in your offhand\n"
							+ "Throw them off your head by right clicking. The further your holding distance,"
							+ " the further they'll go*. You can also fling them away by moving them and letting go\n"
							+ "*(Pressing shift will show you the direction you'll throw them in!)\n"
							+ "To annoy someone, grab them and put them into lava\n"
							+ "The crafting recipe, holding distances, allowing riding, allowing grabbing players,"
							+ " and who can use the /mbc give command can be changed in plugins/MobPlugin"
							+ "/config.yml");
					sender.sendMessage(ChatColor.GREEN + "Plugin created by toomuchzelda\nThank you!");
				}
			}
		}
		return true;
	}

	//only for use in onEnable() - kill any leftover pigs from a crash or other bad shutdown
	private static void killLeftovers()
	{
		for(World world : Bukkit.getWorlds())
		{
			for(Entity e : world.getEntities())
			{
				if(e.getPersistentDataContainer().has(ControlledMob.metaKey, PersistentDataType.INTEGER))
				{
					if(e.isValid())
					{
						e.remove();
						Bukkit.getLogger().info("Removed " + e.getName());
					}
				}
			}
		}
	}
	
	public static MobPlugin getMobPlugin()
	{
		return _mobPlugin;
	}
}
