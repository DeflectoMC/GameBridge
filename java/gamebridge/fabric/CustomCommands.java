package gamebridge.fabric;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.AllowCommand;
import net.fabricmc.fabric.api.event.Event;

/** Planned to be used later
*   when more commands are added
*
*   A similar system is currently used by GameBridgeMod.java
*/
public class CustomCommands {
	
	private static Map<String, Consumer<String[]>> commands = new HashMap<>();
	private static Map<String, String> usages = new HashMap<>();
	private static boolean registered = false;
	
	public static void addCommand(String name, String usage, Consumer<String[]> executor) {
		register();
		commands.put(name, executor);
		usages.put(name, usage);
	}
	
	public static void addCommand(String name, Consumer<String[]> executor) {
		register();
		commands.put(name, executor);
		usages.put(name, null);
	}
	
	private static void register() {
		if (registered) return;
		registered = true;
		//Causes custom commands to be ran client-side
		//when they are typed, and not send it to the server
		ClientSendMessageEvents.ALLOW_COMMAND.register(Event.DEFAULT_PHASE, new AllowCommand() {

			@Override
			public boolean allowSendCommandMessage(String command) {
				String[] split = command.split("\\s+");
				command = split[0].toLowerCase();
				String[] args = new String[split.length-1];
				for (int i = 0; i < args.length; i++) {
					args[i] = split[i+1];
				}
				if (!commands.containsKey(command)) {
					return true;
				}
				try {
					commands.get(command).accept(args);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
				return false;
			}
			
		});
		
		//Causes commands to be valid client-side when typed,
		//and not red. Also, they become able to be tab completed
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			for (var entry : usages.entrySet()) {
				var name = entry.getKey();
				var cmd = ClientCommandManager.literal(name);
				if (entry.getValue() != null) {
					var arg = ClientCommandManager.argument(entry.getValue(), StringArgumentType.greedyString());
					cmd = cmd.then(arg);
				}
				dispatcher.register(cmd);
			}
		});
	}

}
