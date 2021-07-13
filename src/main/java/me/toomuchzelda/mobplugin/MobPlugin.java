package me.toomuchzelda.mobplugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
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

	@Override
	public void onEnable()
	{
		_mobPlugin = this;

		config.addDefault("minimumDistance", 1.2d);
		config.addDefault("maximumDistance", 30);
		config.addDefault("craftable", true);
		config.addDefault("give-command-ops-only", false);
		config.addDefault("allow-backpack", true);

		config.options().copyDefaults(true);
		saveConfig();

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
		if(cmd.getName().equalsIgnoreCase("mobcontroller"))
		{
			if(args.length == 0)
			{
				sender.sendMessage(ChatColor.YELLOW + "type /mbc help for how to use");
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
					sender.sendMessage("Running PaperMC: " + MobController.isPaperMC);
				}
				else if(args[0].equals("help"))
				{
					sender.sendMessage("===Mob Grabber===");
					sender.sendMessage("/mbc - get the Mob Grabber item");
					sender.sendMessage("Point at a mob and right click the item to pick them up!\n"
							+ "To drop them, hold the Mob Grabber and right click.\n"
							+ "To move them closer/further, Sneak and scroll up/down your hotbar\n"
							+ "To throw them, left click with your Grabber item or fling them around"
							+ "and drop (right click while holding item) at the right time\n"
							+ "The further you hold them when left clicking the further they'll fly\n"
							+ "Carry a mob on your head by holding the Grabber item in your offhand\n"
							+ "To annoy someone, grab them and put them into lava\n"
							+ "The crafting recipe, holding distances, allowing riding, and who can use"
							+ " the /mbc give command can be changed in MobPlugin"
							+ "/config.yml");
					sender.sendMessage("/mbc help - view this page");
					sender.sendMessage("Plugin created by toomuchzelda");
				}
			}
		}
		return true;
	}

	public static MobPlugin getMobPlugin()
	{
		return _mobPlugin;
	}
}
