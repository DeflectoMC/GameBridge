package gamebridge;

import java.io.File;
import java.lang.reflect.Method;
import java.net.Socket;
import java.nio.file.Files;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.ice4j.ice.AgentConfig;
import org.jitsi.config.JitsiConfig;

import gamebridge.nexus.NexusClient;

public class GameBridge {
	
	private static Logger log = Logger.getLogger("GameBridge");
	private static Level level = Level.FINEST;
	
	public static class LogEntry {
		public final Level level;
		public final String message;
		public final Exception exception;
		
		public LogEntry(Level level, String message, Exception ex) {
			this.level = level;
			this.message = message;
			this.exception = ex;
		}
	}
	
	public static Consumer<LogEntry> logFunction = (entry) -> {
		if (entry.level.intValue() >= GameBridge.level.intValue()) {
			if (entry.exception != null) {
				log.log(Level.WARNING, entry.message, entry.exception);
			} else if (entry.level != Level.WARNING) {
				log.log(Level.INFO, entry.message);
			} else {
				log.log(Level.WARNING, entry.message);
			}
		}
	};
	
	public static void fixICEConfigIssues(File config) throws Exception {
		
		if (!config.exists()) {
			Files.writeString(config.toPath(), GameBridgeConfigSource.reference_conf);
			Thread.sleep(2000l);
		}
		
		System.setProperty("config.file", config.getAbsolutePath());
		//JitsiConfig.Companion.useDebugNewConfig(source);
		//JitsiConfig.Companion.useDebugLegacyConfig(source);
		JitsiConfig.Companion.reloadNewConfig();
		JitsiConfig.Companion.getTypesafeConfig();
		
		for (Method m : AgentConfig.config.getClass().getDeclaredMethods()) {
			if (m.canAccess(AgentConfig.config) && m.getParameters().length == 0) {
				m.invoke(AgentConfig.config);
			}
		}
	}
	
	public static void setLogger(Logger logger) {
		log = logger;
	}
	
	public static void setLoggingLevel(Level logLevel) {
		GameBridge.level = logLevel;
	}
	
	public static Level getLoggingLevel() {
		return level;
	}
	
	public static void info(String message) {
		logFunction.accept(new LogEntry(Level.INFO, message, null));
	}
	
	public static void warning(String message) {
		logFunction.accept(new LogEntry(Level.WARNING, message, null));
	}
	
	public static void log(Level level, String message) {
		logFunction.accept(new LogEntry(level, message, null));
	}
	
	public static void log(Level level, String message, Exception ex) {
		logFunction.accept(new LogEntry(level, message, ex));
	}
	
	public static boolean isPortInUse(int port) {
		try (Socket test = new Socket("localhost", port)) {
			
		} catch (Exception ex) {
			return false;
		}
		return true;
	}
	
	public static void main(String[] args) {
		if (args.length == 2 && args[0].equalsIgnoreCase("host")) {
			int port;
			try {
				port = Integer.parseInt(args[1]);
			} catch (Exception ex) {
				System.out.println("Invalid port: " + args[1]);
				return;
			}
			
			tryHost(port);
			return;
		} else if (args.length == 3 && args[0].equalsIgnoreCase("join")) {
			int port;
			try {
				port = Integer.parseInt(args[1]);
			} catch (Exception ex) {
				System.out.println("Invalid port: " + args[1]);
				return;
			}
			
			tryJoin(port, args[2]);
			
		} else {
			askUserInput();
		}
	}
	
	private static boolean tryHost(int port) {
		
		if (port < 1000 || port > 65535) {
			System.out.println("Invalid port number: " + port + ". Ports can only be in the range of 1000-65535");
			return false;
		}
		
		if (!isPortInUse(port)) {
			System.out.println("There is not a server/world currently running on port " + port);
			return false;
		}
		
		System.out.println("Attempting to host...");
		try {
			var host = createHost(port);
			System.out.println("Successfully hosting. Server ID: " + host.getID());
		} catch (Exception ex) {
			ex.printStackTrace();
			System.out.println("Failed to host");
			try {
				Thread.sleep(5000l);
			} catch (Exception ex2) {}
			
			return false;
		}
		return true;
	}
	
	private static boolean tryJoin(int port, String hostID) {
		
		if (port < 1000 || port > 65535) {
			System.out.println("Invalid port number: " + port + ". Ports can only be in the range of 1000-65535");
			return false;
		}
		
		if (isPortInUse(port)) {
			System.out.println("The port " + port + " is currently in use");
			return false;
		}
		
		if (hostID == null || hostID.length() < 10 || !hostID.matches("^[0-9a-z]+$")) {
			System.out.println("Invalid host ID: \"" + hostID + "\"");
			return false;
		}
		
		System.out.println("Attempting to join...");
		try {
			createClient(port, hostID);
			System.out.println("Successfully joined");
		} catch (Exception ex) {
			ex.printStackTrace();
			System.out.println("Failed to join");
			try {
				Thread.sleep(5000l);
			} catch (Exception ex2) {}
			
			return false;
		}
		
		return true;
	}
	
	private static void sendCommands() {
		System.out.println("");
		System.out.println("Commands:");
		System.out.println("/host <port>");
		System.out.println("/gjoin <port> <id>");
	}

	@SuppressWarnings("resource")
	private static void askUserInput() {
		int port = 0;
		String hostID = null;
		
		sendCommands();
		
		Scanner sc = new Scanner(System.in);
		
		while (sc.hasNextLine()) {
			
			String command = sc.nextLine();
			
			Scanner cmdScanner = new Scanner(command);
			if (command.startsWith("/host")) {
				try {
					cmdScanner.skip("/host\\s+");
					port = cmdScanner.nextInt();
				} catch (Exception ex) {
					System.out.println("Usage: /host <port number>");
					
					sendCommands();
					continue;
				}
				
				if (tryHost(port)) return;
				
				sendCommands();
				continue;

			} else if (command.startsWith("/gjoin")) {
				try {
					cmdScanner.skip("/gjoin\\s+");
					port = cmdScanner.nextInt();
				} catch (Exception ex) {
					System.out.println("Usage: /gjoin <port number> <id>");
					sendCommands();
					continue;
				}
				if (port < 1000 || port > 65535) {
					System.out.println("Invalid port number: " + port + ". Ports can only be in the range of 1000-65535");
					sendCommands();
					continue;
				}
				try {
					cmdScanner.skip("\\s+");
				} catch (Exception ex) {
					System.out.println("Usage: /gjoin <port number> <id>");
					sendCommands();
					continue;
				}
				try {
					hostID = cmdScanner.next("[0-9a-z]+");
				} catch (NoSuchElementException ex) {
					System.out.println("Couldn't find a valid ID from command " + command);
					sendCommands();
					continue;
				}
				
				if (tryJoin(port, hostID)) return;
				
				
				sendCommands();
				continue;
			}
		}
	}

	public static ClientPeerSocket createClient(int gamePort, String hostID) throws Exception {
		Ice ice = new Ice();
		String localSDP = ice.getLocalSDP();
		var response = NexusClient.create().join(hostID, localSDP, 45, TimeUnit.SECONDS);
		String remoteSDP;
		try {
			remoteSDP = response.get().trim();
		} catch (Exception ex) {
			ice.close();
			throw ex;
		}
		
		System.out.println("Received SDP from host");

		ice.setRemoteSDP(remoteSDP);

		var socket = ice.establish(60, TimeUnit.SECONDS).get();

		var clientSocket = new ClientPeerSocket(gamePort, socket, hostID);
		clientSocket.start();
		return clientSocket;
	}

	public static GameBridgeHost createHost(int gamePort) throws Exception {
		return new GameBridgeHost(gamePort);
	}
	
	public static GameBridgeHost createHost(int gamePort, byte[] privateKey, int preferredIDLength) throws Exception {
		return new GameBridgeHost(gamePort, privateKey, preferredIDLength);
	}

}
