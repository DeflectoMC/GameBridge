package gamebridge.fabric;

import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

import com.mojang.brigadier.arguments.StringArgumentType;

import gamebridge.ClientPeerSocket;
import gamebridge.GameBridge;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.AllowCommand;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class GameBridgeMod implements ModInitializer {
	
	private static MinecraftClient mc = MinecraftClient.getInstance();
	
	private static ExecutorService executor = Executors.newCachedThreadPool();
	
	private CompletableFuture<Void> joinTask;
	
	private ClientPeerSocket gameBridgeClient;
	
	public static void msg(String message) {
		var player = mc.player;
		if (player != null) {
			player.sendMessage(Text.of(message));
		}
	}
	
	public static void copymsg(String message, String copyToClipboard) {
		var text = Text.literal(message)
		.setStyle(Style.EMPTY.withBold(true).withColor(Formatting.GREEN)
		.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
		Text.literal("Click to copy:\n\n" + copyToClipboard)))
		.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD,
		copyToClipboard)));
		
		var player = mc.player;
		if (player != null) {
			player.sendMessage(text);
		}
	}
	
	private void join(String command) {
		
		@SuppressWarnings("resource")
		Scanner sc = new Scanner(command);
		String joinID;
		try {
			sc.skip("gjoin\\s+");
			joinID = sc.next("[a-z0-9]+");
		} catch (Exception ex) {
			msg("§c[GameBridge] Usage: /gjoin <server id>");
			return;
		}
		
		if (joinID.length() < 10) {
			msg("§c[GameBridge] Invalid server id: " + joinID);
			return;
		}
		
		final int port = 35585;
		
		final String ip = "127.0.0.1:" + port;
		
		if (gameBridgeClient != null) {
			if (gameBridgeClient.getServerID().equals(joinID)) {
				copymsg("§3[GameBridge] You are already connected to " + joinID + ". Click to copy the server IP", ip);
				return;
			}
			msg("§7§oClosing existing proxy...");
			gameBridgeClient.close();
			try {
				Thread.sleep(5000l);
			} catch (Exception ex) {}
			gameBridgeClient = null;
			msg("§aClosed existing proxy");
		}
		
		msg("§7§oCreating a proxy...");
		try {
			gameBridgeClient = GameBridge.createClient(port, joinID);
		} catch (Exception ex) {
			ex.printStackTrace();
			msg("§cAn internal error occurred. Perhaps the server is not online? Check [minecraft folder]/logs/latest.log for more info");
			return;
		}
		
		msg("§7§oSuccessfully connected to " + joinID);
		msg("§aYou may now join the server!");
		copymsg("§b§lClick to Copy Server IP", ip);
        
		
	}

	@Override
	public void onInitialize() {
		
		GameBridge.setLoggingLevel(Level.INFO);
		
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			{
				var join = ClientCommandManager.literal("gjoin");
				var id = ClientCommandManager.argument("server id", StringArgumentType.greedyString());
				
				join = join.then(id);
				dispatcher.register(join);
				
				
			}
		});
		
		ClientSendMessageEvents.ALLOW_COMMAND.register(Event.DEFAULT_PHASE, new AllowCommand() {

			@Override
			public boolean allowSendCommandMessage(String command) {
				
				if (command.startsWith("gjoin")) {
					
					if (joinTask != null && !joinTask.isDone()) {
						msg("§cYou are currently trying to join a server already; please wait until it is finished");
						return false;
					}
					
					final var cmd = command;
					
					joinTask = CompletableFuture.runAsync(() -> {
						join(cmd);
					}, executor);
					
					return false;
				}
				
				return true;
			}
			
		});
		
		
	}

}
