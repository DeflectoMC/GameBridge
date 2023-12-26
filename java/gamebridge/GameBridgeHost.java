package gamebridge;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import gamebridge.nexus.NexusClient;
import gamebridge.nexus.NexusClient.JoinRequest;

public class GameBridgeHost {
	
	private final ExecutorService executor = Executors.newCachedThreadPool();
	
	private NexusClient nexusClient;
	
	private String hostID;
	
	private final int tcpPort;
	
	public String getID() {
		return hostID;
	}
	
	public GameBridgeHost(int tcpPort) throws Exception {
		this.tcpPort = tcpPort;
		nexusClient = NexusClient.create();
		var hostID = nexusClient.host(this::onJoinAsync, 30, TimeUnit.SECONDS);
		this.hostID = hostID.get();
	}
	
	public GameBridgeHost(int tcpPort, byte[] privateKey, int preferredIDLength) throws Exception {
		this.tcpPort = tcpPort;
		nexusClient = NexusClient.create();
		var hostID = nexusClient.host(this::onJoinAsync, privateKey, preferredIDLength, 30, TimeUnit.SECONDS);
		this.hostID = hostID.get();
	}

	private void onJoinAsync(JoinRequest request) {
		GameBridge.log(Level.FINE, "Received SDP from client");
		executor.execute(() -> {
			try {
				onJoin(request);
			} catch (Exception ex) {
				GameBridge.log(Level.WARNING, "Error while handling SDP from client", ex);
				GameBridge.log(Level.FINEST, "Client SDP:");
				GameBridge.log(Level.FINEST, request.client_sdp());
			}
		});
	}
	
	private Set<HostPeerSocket> connections = Collections.newSetFromMap(new WeakHashMap<>());
	
	private void onJoin(JoinRequest request) throws Exception {
		var ice = new Ice();
		var localSDP = ice.getLocalSDP();
		var remoteSDP = request.client_sdp();
		
		request.reply(localSDP);
		
		ice.setRemoteSDP(remoteSDP);
		
		var socket = ice.establish(50, TimeUnit.SECONDS).get();
		
		var hostSocket = new HostPeerSocket(tcpPort, socket);
		connections.add(hostSocket);
		hostSocket.start();
	}
	
	public void close() {
		if (nexusClient != null) {
			nexusClient.close();
		}
		connections.forEach(conn -> conn.close());
		connections.clear();
	}

}
