package gamebridge.plugin;

import java.io.File;
import java.security.SecureRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.logging.Level;

import org.bouncycastle.util.Arrays;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import gamebridge.GameBridge;
import gamebridge.GameBridgeHost;
import gamebridge.nexus.Login;
import gamebridge.security.KeyStorage;

import static java.nio.charset.StandardCharsets.UTF_8;

public class GameBridgePlugin extends JavaPlugin {

	public GameBridgeHost hostInstance;
	public CompletableFuture<Void> createHostTask;

	private static final String salt = "I#@*%&jfrdejtgur754tyhgtsrd5e5w7$(^TObynltry6u85uj7o549";

	@Override
	public void onEnable() {

		saveDefaultConfig();

		File conf = new File(this.getDataFolder(), "reference.conf");
		try {
			GameBridge.fixICEConfigIssues(conf);
		} catch (Exception ex) {
			ex.printStackTrace();
			Bukkit.getPluginManager().disablePlugin(this);
			return;
		}

		GameBridge.setLogger(this.getLogger());

		String level = getConfig().getString("logging", "INFO").toUpperCase();

		try {
			Level logLevel = Level.parse(level);
			GameBridge.setLoggingLevel(logLevel);
		} catch (IllegalArgumentException | NullPointerException ex) {
			this.getLogger().log(Level.WARNING, "Failed to parse logging level " + level);
			Bukkit.getPluginManager().disablePlugin(this);
			return;
		}

		if (!getConfig().getBoolean("disable-hosting")) {

			final int serverIDLength = getConfig().getInt("id-mode", 10);
			if (serverIDLength < 10) {
				GameBridge.warning("Error: ID mode cannot be less than 10");
				Bukkit.getPluginManager().disablePlugin(this);
				return;
			}
			
			if (serverIDLength > 50) {
				GameBridge.warning("Error: ID mode cannot be more than 50");
				Bukkit.getPluginManager().disablePlugin(this);
				return;
			}

			byte[] key = null;
			
			final boolean useSavedKey = serverIDLength > 10;

			if (useSavedKey) {
				
				
				try (KeyStorage k = new KeyStorage(new File(this.getDataFolder(), "keystore.db"))) {
					key = k.getKey("server-private-key");
					if (key != null && key.length != 32) {
						this.getLogger().log(Level.WARNING, "Wrong key length. Generating a new key");
						key = null;
					}

					if (key == null) {
						key = new byte[32];
						new SecureRandom().nextBytes(key);

						k.setKey("server-private-key", key);

						this.getLogger().info("Created private user key");
					}

					this.getLogger().info("Loaded private server key");
				} catch (Exception ex) {
					this.getLogger().log(Level.WARNING, "Failed to edit keystorage.db", ex);
					ex.printStackTrace();
					Bukkit.getPluginManager().disablePlugin(this);
					return;
				}

				key = Login.hash(
						key,
						salt.getBytes(UTF_8)
						);
				
				key = Arrays.copyOfRange(key, 0, 32);
			}

			int port = Bukkit.getServer().getPort();

			this.getLogger().info("Starting hosting...");
			this.getLogger().info("Creating a host ID...");

			final byte[] privateKey = key;

			createHostTask =
					CompletableFuture.runAsync(() -> {
						try {

							if (useSavedKey) {
								hostInstance = GameBridge.createHost(port, privateKey, serverIDLength);
							} else {
								hostInstance = GameBridge.createHost(port);
							}
						} catch (Exception ex) {
							this.getLogger().log(Level.WARNING, "An exception occurred while getting the host ID:", ex);
							return;
						}

						this.getLogger().info("Successfully hosting! Players can now join.");
						this.getLogger().info("Join Command: /gjoin " + hostInstance.getID());
					}, Executors.newCachedThreadPool());
		}
	}

	@Override
	public void onDisable() {
		if (createHostTask != null) {
			createHostTask.cancel(true);
		}
		if (hostInstance != null) {
			hostInstance.close();
			this.getLogger().info("Stopped hosting");
		}
	}

	private static final String github_link = "https://github.com/DeflectoMC/GameBridge";

	@Override
	public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {

		sender.sendMessage("§3[GameBridge] Join any server using the GameBridge mod! Download it here: " + github_link);

		if (hostInstance == null) {
			if (!getConfig().getBoolean("disable-hosting")) {
				sender.sendMessage("§3[GameBridge] §cHosting is not currently enabled on this server.");
				return true;
			}
			sender.sendMessage("§3[GameBridge] §cThe server isn't currently hosting due to an internal error. Check console logs for more info");
			return true;
		}

		sender.sendMessage("§3[GameBridge] §eCurrent Join Command: /gjoin " + hostInstance.getID());
		return true;
	}

}
