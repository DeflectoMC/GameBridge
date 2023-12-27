package gamebridge.fabric;

import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.arguments.StringArgumentType;

import gamebridge.ClientPeerSocket;
import gamebridge.GameBridge;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.AllowGame;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.AllowCommand;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Init;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Join;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.network.ServerInfo.ServerType;
import net.minecraft.client.option.ServerList;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;

public class GameBridgeMod implements ModInitializer {

	public static final int join_port = 35585;
	public static final String join_address = "127.0.0.1:" + 35585;

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

	public static void cmdmsg(String message, String command) {
		var text = Text.literal(message)
				.setStyle(Style.EMPTY.withBold(true).withColor(Formatting.GREEN)
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
								Text.literal("Click to run command:\n\n" + command)))
						.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
								command)));

		var player = mc.player;
		if (player != null) {
			player.sendMessage(text);
		}
	}

	private void auto() {
		if (joinTask != null && !joinTask.isDone()) {
			msg("§cConnection is currently in progress, please wait until it is finished");
			return;
		}



		if (gameBridgeClient == null || gameBridgeClient.isClosed() || gameBridgeClient.isPeerSocketClosed()) {
			msg("§cNo server to join currently");
			return;
		}

		var list = new ServerList(mc);
		list.loadFile();

		boolean listedAddress = false;

		for (int i = 0; i < list.size(); i++) {
			var info = list.get(i);
			if (join_address.equals(info.address)) {
				listedAddress = true;
				break;
			}
		}

		var info = new ServerInfo("GameBridge", join_address, ServerType.OTHER);

		if (!listedAddress) {
			list.add(info, false);
			list.saveFile();
		}

		var addr = new ServerAddress("127.0.0.1", join_port);


		boolean bl = mc.isInSingleplayer();
		mc.world.disconnect();
		if (bl) {
			mc.disconnect(new MessageScreen(Text.translatable("menu.savingLevel")));
		} else {
			mc.disconnect();
		}
		ConnectScreen.connect(new TitleScreen(), mc, addr, info, true);

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

		if (gameBridgeClient != null) {
			if (gameBridgeClient.getServerID().equals(joinID)) {
				copymsg("§3[GameBridge] You are already connected to " + joinID + ". Click to copy the server IP", join_address);
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
			gameBridgeClient = GameBridge.createClient(join_port, joinID);
		} catch (Exception ex) {
			ex.printStackTrace();
			msg("§cAn internal error occurred. Perhaps the server is not online? Check [minecraft folder]/logs/latest.log for more info");
			return;
		}

		msg("§7§oSuccessfully connected to " + joinID);
		msg("§aYou may now join the server!");
		copymsg("§b§lClick to Copy Server IP", join_address);
		cmdmsg("§6§lClick to Instantly Join", "/gauto");

	}

	private long lastJoin = 0l;

	private static final String message_bug = "multiplayer.message_not_delivered";

	@Override
	public void onInitialize() {
		
		System.out.println("Initializing via System.out");
		final  var modLog = LoggerFactory.getLogger("gamebridge");
		modLog.info("Initializing from slf4j. Ok");

		GameBridge.setLoggingLevel(Level.INFO);
		GameBridge.logFunction = (entry) -> {
			
			var msg = entry.message;
			var ex = entry.exception;
			if (entry.level == Level.INFO) {
				if (ex != null) {
					modLog.info("[GameBridge] " + msg, ex);
				} else {
					modLog.info("[GameBridge] " + msg);
				}
			}
			else if (entry.level == Level.WARNING) {
				if (ex != null) {
					modLog.warn("[GameBridge] " + msg, ex);
				} else {
					modLog.warn("[GameBridge] " + msg);
				}
			} else if (entry.level.intValue() <= Level.FINEST.intValue()) {
				if (ex != null) {
					modLog.trace("[GameBridge] " + msg, ex);
				} else {
					modLog.trace("[GameBridge] " + msg);
				}
			} else if (entry.level.intValue() <= Level.FINE.intValue()) {
				if (ex != null) {
					modLog.debug("[GameBridge] " + msg, ex);
				} else {
					modLog.debug("[GameBridge] " + msg);
				}
			}
		};
		
		GameBridge.info("Initializing");

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			{
				var join = ClientCommandManager.literal("gjoin");
				var id = ClientCommandManager.argument("server id", StringArgumentType.greedyString());

				join = join.then(id);
				dispatcher.register(join);

				dispatcher.register(ClientCommandManager.literal("gauto"));


			}
		});

		ClientPlayConnectionEvents.INIT.register(new Init() {

			@Override
			public void onPlayInit(ClientPlayNetworkHandler handler, MinecraftClient client) {
				GameBridge.info("Entered init phase");
				lastJoin = System.currentTimeMillis();
			}
		});
		ClientPlayConnectionEvents.JOIN.register(new Join() {


			@Override
			public void onPlayReady(ClientPlayNetworkHandler handler, PacketSender sender, MinecraftClient client) {
				GameBridge.info("Entered join phase");
				lastJoin = System.currentTimeMillis();
			}


		});

		ClientReceiveMessageEvents.ALLOW_GAME.register(new AllowGame() {

			@Override
			public boolean allowReceiveGameMessage(Text text, boolean overlay) {
				GameBridge.info("Received chat message: " + text.getString());
				if (lastJoin <= System.currentTimeMillis() - 10_000l) return true;
				GameBridge.info("Recently joined");
				if (gameBridgeClient == null || gameBridgeClient.isClosed()) return true;
				GameBridge.info("Checking for chat bugs");
				if (text instanceof MutableText == false) return true;
				GameBridge.info("Text is mutable");
				var mutable = (MutableText)text;
				if (mutable.getContent() instanceof TranslatableTextContent == false) return true;
				GameBridge.info("Text is translatable");
				var content = (TranslatableTextContent)mutable.getContent();
				if (!message_bug.equals(content.getKey())) return true;
				GameBridge.info("Text is correct, cancelling");
				return false;
			}

		});

		ClientSendMessageEvents.ALLOW_COMMAND.register(Event.DEFAULT_PHASE, new AllowCommand() {

			@Override
			public boolean allowSendCommandMessage(String command) {

				if (command.startsWith("gauto")) {
					auto();
					return false;
				}

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
