package gamebridge.nexus;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import gamebridge.GameBridge;

class NexusClientImpl extends WebSocketClient implements NexusClient {
	
	private ExecutorService executor = Executors.newCachedThreadPool();
	
	private String hostID;
	
	private CompletableFuture<String> host;
	private CompletableFuture<String> join;
	
	private byte[] hostJoin = new byte[0];
	
	private ByteBuffer joinMessage;
	
	public NexusClientImpl(URI serverUri, Map<String, String> httpHeaders) {
		super(serverUri, httpHeaders);
	}
	
	private Consumer<JoinRequest> onJoin;

	public CompletableFuture<String> host(Consumer<JoinRequest> onJoin, int timeout, TimeUnit unit) {
		this.onJoin = onJoin;
		host = new CompletableFuture<String>()
		.orTimeout(timeout, unit);
		this.connect();
		return host;
	}
	
	
	
	public CompletableFuture<String> host(Consumer<JoinRequest> onJoin, byte[] id, int preferredIDLength, int timeout, TimeUnit unit) {

		this.hostJoin = Login.generate(id, preferredIDLength);
		Login.verify(hostJoin);
		
		this.onJoin = onJoin;
		host = new CompletableFuture<String>()
		.orTimeout(timeout, unit);
		this.connect();
		return host;
	}
	private Random rng = new Random();
	
	private long lastClient = System.currentTimeMillis();
	
	private void hostKeepAlive() {
		int g = 5;
		if (g == 5) return;
		while (!this.isClosed() && lastClient >= System.currentTimeMillis() - 60 * 60 * 3) {
			try {
				Thread.sleep(25000l);
			} catch (Exception ex) {
				break;
			}
			
			if (this.isClosed()) {
				break;
			}
			var msg = new byte[rng.nextInt(3) + 3];
			rng.nextBytes(msg);
			this.send(ByteBuffer.wrap(msg));
			GameBridge.log(Level.FINER, "Sent keepAlive");
		}
		GameBridge.log(Level.FINE, "keepAlive loop ended");
	}
	
	public class JoinRequestImpl implements NexusClient.JoinRequest {
		
		private final byte[] client_id;
		private final String client_sdp;
		
		
		public JoinRequestImpl(byte[] clientID, String clientSDP) {
			this.client_id = clientID;
			this.client_sdp = clientSDP;
		}
		
		public void reply(String localSDP) {
			byte[] sdp_bytes = (localSDP == null) ? new byte[0] : localSDP.getBytes(StandardCharsets.UTF_8);
			var message = ByteBuffer.allocate(1 + client_id.length + sdp_bytes.length);
			message.put((byte)client_id.length);
			message.put(client_id);
			message.put(sdp_bytes);
			message.rewind();
			
			GameBridge.log(Level.FINE, "Replying with local SDP");
			
			NexusClientImpl.this.send(message);
		}

		@Override
		public String client_sdp() {
			return client_sdp;
		}

		@Override
		public byte[] client_id() {
			return client_id;
		}
		
	}
	
	public CompletableFuture<String> join(String hostID, String localSDP, int timeout, TimeUnit unit) {
		join = new CompletableFuture<String>()
		.orTimeout(timeout, unit);
		
		join.exceptionally(ex -> {
			this.close();
			return null;
		});
		
		byte[] host_id_bytes = hostID.getBytes(StandardCharsets.UTF_8);
		byte[] sdp_bytes = localSDP.getBytes(StandardCharsets.UTF_8);
		
		this.joinMessage = ByteBuffer.allocate(1 + host_id_bytes.length + sdp_bytes.length);
		this.joinMessage.put((byte)host_id_bytes.length);
		this.joinMessage.put(host_id_bytes);
		this.joinMessage.put(sdp_bytes);
		
		this.joinMessage.rewind();
		
		this.connect();
		return join;
	}

	@Override
	public void onOpen(ServerHandshake handshakedata) {
		GameBridge.info("WebSocket opened");
		if (host != null) {
			this.send(hostJoin);
			executor.execute(this::hostKeepAlive);
		} else if (join != null) {
			this.send(joinMessage);
		}
	}

	@Override
	public void onMessage(String message) {}
	
	@Override
	public void onMessage(ByteBuffer message) {
		if (host != null) {
			
			if (hostID == null) {
				if (message.remaining() <= 0) {
					this.close();
					host.completeExceptionally(new Exception("Failed to get Host ID from Nexus"));
					return;
				}
				byte[] idBytes = new byte[message.remaining()];
				message.get(idBytes);
				hostID = new String(idBytes);
				host.complete(hostID);
				
				return;
			}
			
			if (message.remaining() <= 0) return;
			
			int join_id_length = Byte.toUnsignedInt(message.get());
			byte[] join_id = new byte[join_id_length];
			message.get(join_id);
			byte[] join_sdp_bytes = new byte[message.remaining()];
			message.get(join_sdp_bytes);
			
			String joinSDP = new String(join_sdp_bytes, StandardCharsets.UTF_8);
			
			JoinRequestImpl req = new JoinRequestImpl(join_id, joinSDP);
			
			this.onJoin.accept(req);
			
		} else if (join != null) {
			if (message.remaining() <= 0) {
				this.close();
				join.completeExceptionally(new Exception("Failed to get SDP from Host"));
				return;
			}
			byte[] sdpBytes = new byte[message.remaining()];
			message.get(sdpBytes);
			String remoteSDP = new String(sdpBytes, StandardCharsets.UTF_8);
			join.complete(remoteSDP);
			this.close();
		}
	}

	@Override
	public void onClose(int code, String reason, boolean remote) {
		GameBridge.info("WebSocket closed");
		this.executor.shutdownNow();
		if (join != null) join.cancel(true);
	}

	@Override
	public void onError(Exception ex) {
		GameBridge.log(Level.WARNING, "WebSocket error", ex);
	}

}
