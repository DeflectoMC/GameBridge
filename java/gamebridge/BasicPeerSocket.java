package gamebridge;

import java.io.InterruptedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public abstract class BasicPeerSocket extends PeerSocket {
	
	private long lastKeepAlive;
	
	private CompletableFuture<Void> ping = new CompletableFuture<>();

	public BasicPeerSocket(IceSocket socket) {
		super(socket);
		
		this.registerChannel("ping", 253, () -> {
			GameBridge.log(Level.FINEST, "Ponging back");
			this.send("pong");
			GameBridge.log(Level.FINEST, "Sent pong");
		});
		
		this.registerChannel("pong", 254, (packet) -> {
			GameBridge.log(Level.FINE, "Received pong");
			if (this.pingCount > 0) {
				this.pingCount--;
				ping.complete(null);
				GameBridge.log(Level.INFO, "Pong!");
			}
		});
		
		this.registerChannel("keepAlive", 255, () -> {
			GameBridge.log(Level.FINER, "Received keepAlive from peer");
			this.lastKeepAlive = System.currentTimeMillis();
		});
	}
	
	private void keepAliveLoop() {
		try {
			Thread.sleep(8000l);
		} catch (Exception ex) {
			return;
		}
		while (keepAlive()) {
			try {
				Thread.sleep(5000l);
			} catch (InterruptedException ex) {}
		}
	}
	
	private boolean keepAlive() {
		if (lastKeepAlive <= System.currentTimeMillis() - 45_000) {
			GameBridge.log(Level.WARNING, "A connection timed out");
			this.close();
			return false;
		}
		var ex = this.send("keepAlive");
		if (ex instanceof InterruptedIOException) return false;
		return true;
	}
	
	@Override
	public void start() {
		GameBridge.log(Level.FINER, this.getClass().getSimpleName() + ".start()");
		this.lastKeepAlive = System.currentTimeMillis();
		this.executor.submit(this::keepAliveLoop);
		super.start();
	}
	
	
	public CompletableFuture<Void> ping() {
		ping = new CompletableFuture<>();
		GameBridge.log(Level.INFO, "Pinging");
		this.pingCount++;
		this.send("ping");
		GameBridge.log(Level.FINE, "Sent ping");
		return ping;
	}
	
	private int pingCount = 0;

}
