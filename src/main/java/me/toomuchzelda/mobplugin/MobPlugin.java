package me.toomuchzelda.mobplugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

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
		if(sender instanceof Player) {
			Player p =  (Player) sender;
			if(cmd.getName().equalsIgnoreCase("mobcontroller")) 
			{
				if(args.length == 0)
				{
					p.getInventory().addItem(_mobController._controllerItem);
					sender.sendMessage("�9Given mob grabber");
				}
				else if(args.length > 0)
				{
					if(args[0].equals("debug"))
					{
						p.sendMessage(MobController.getMap().toString());
						p.sendMessage("Running PaperMC: " + MobController.isPaperMC);
						p.sendMessage("awesome: " + config.getBoolean("youAreAwesome"));
					}
				}
				
//				for(int i = 0; i < args.length; i++)
//				{
//					p.sendMessage(args[i]);
//				}
				
				return true;
			}
			return true;
		}
		else
		{
			sender.sendMessage("�4Something went wrong, or this command wasn't run by a player.");
			return false;
		}
	}
	
	public static MobPlugin getMobPlugin()
	{
		return _mobPlugin;
	}
}
