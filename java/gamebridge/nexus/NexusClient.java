package gamebridge.nexus;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public interface NexusClient {
	
	public static NexusClient create() {
		URI uri;
		try {
			uri = new URI(Nexus.url);
		} catch (URISyntaxException ex) {
			throw new Error(ex);
		}
		
		Map<String, String> headers = new HashMap<>(1);
		headers.put("User-Agent", "GameBridge Nexus Client");
		return new NexusClientImpl(uri, headers);
	}
	
	public CompletableFuture<String> host(Consumer<JoinRequest> onJoin, byte[] id, int preferredIDLength, int timeout, TimeUnit unit);
	
	public CompletableFuture<String> host(Consumer<JoinRequest> onJoin, int timeout, TimeUnit unit);
	
	public CompletableFuture<String> join(String hostID, String localSDP, int timeout, TimeUnit unit);
	
	public static interface JoinRequest {
		
		public String client_sdp();
		public byte[] client_id();
		
		public void reply(String localSDP);
	}
	
	public void close();

}
