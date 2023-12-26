package gamebridge;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;

public abstract class PeerSocket {
	
	private Map<String, Byte> channel_names = new HashMap<>(5);
	private Map<Byte, Consumer<ByteBuffer>> channel_handlers = new HashMap<>(5);
	
	private final IceSocket socket;
	
	public PeerSocket(IceSocket socket) {
		this.socket = socket;
	}
	
	private int mtu = 2 * 1024 * 1024;
	
	public void setMTU(int mtu) {
		this.mtu = mtu;
		if (this.sendBuffer != null) this.sendBuffer = new byte[mtu+1];
		if (this.receiveBuffer != null) this.receiveBuffer = new byte[mtu+1];
	}
	
	public void registerChannel(String name, int id) {
		this.registerChannel(name, id, (Consumer<ByteBuffer>)null);
	}
	
	public void registerChannel(String name, int id, Runnable handler) {
		Consumer<ByteBuffer> _handler = (packet) -> {
			handler.run();
		};
		this.registerChannel(name, id, _handler);
	}
	
	public void registerChannel(String name, int id, Consumer<ByteBuffer> handler) {
		if (id < 0 || id > 255) throw new IllegalArgumentException("Invalid channel id");
		if (this.channel_names.containsKey(name)) throw new IllegalStateException("Channel name is already registered");
		if (this.channel_handlers.containsKey((byte)id)) throw new IllegalStateException("Channel number is already registered");
		this.channel_names.put(name, (byte)id);
		this.channel_handlers.put((byte)id, handler);
	}
	
	private byte[] sendBuffer;
	
	private byte[] single = new byte[1];
	
	public synchronized Exception send(String name) {
		return send(name, single, 1, 0);
	}
	
	public synchronized Exception send(String name, ByteBuffer buffer) {
		int off = buffer.position();
		int length = buffer.remaining();
		if (off >= 1 && buffer.hasArray()) {
			byte[] bytes = buffer.array();
			return send(name, bytes, off, length);
		}
		if (sendBuffer == null) {
			sendBuffer = new byte[mtu+1];
		}
		buffer.get(buffer.position(), sendBuffer, 1, length);
		return send(name, sendBuffer, 1, length);
	}
	
	public synchronized Exception send(String name, byte[] bytes, int off, int length) {
		if (off + length > bytes.length) throw new IllegalArgumentException("Length + offset is greater than bytes.length");
		if (!channel_names.containsKey(name)) throw new IllegalArgumentException("Channel name not registered");
		byte id = channel_names.get(name);
		if (off == 0) {
			byte[] bytes2 = new byte[length+1];
			System.arraycopy(bytes, 0, bytes2, 1, length);
			bytes = bytes2;
			off = 1;
		}
		bytes[off-1] = id;
		try {
			this.socket.send(bytes, off-1, length+1);
		} catch (IOException ex) {
			if (!this.socket.isClosed()) GameBridge.log(Level.FINER, "An exception occurred while sending a packet", ex);
			return ex;
		}
		return null;
	}
	
	private byte[] receiveBuffer;
	
	protected final ExecutorService executor = Executors.newCachedThreadPool();
	
	public void start() {
		this.executor.submit(this::receiveLoop);
	}
	
	private void receiveLoop() {
		while (!this.isClosed()) {
			this.receive();
		}
		this.close();
	}
	
	private boolean receive() {
		if (receiveBuffer == null) {
			receiveBuffer = new byte[mtu+1];
		}
		int read;
		try {
			read = this.socket.receive(receiveBuffer);
		} catch (IOException ex) {
			if (!this.socket.isClosed()) GameBridge.log(Level.WARNING, "An exception occurred while sending a packet", ex);
			return false;
		}
		
		byte id = receiveBuffer[0];
		GameBridge.log(Level.FINEST, "Received a packet on channel " + Byte.toUnsignedInt(id));
		var handler = channel_handlers.get(id);
		if (handler != null) {
			var buffer = ByteBuffer.wrap(receiveBuffer);
			buffer.position(1);
			buffer.limit(read);
			GameBridge.log(Level.FINEST, "Handling packet");
			try {
				handler.accept(buffer);
			} catch (Exception ex) {
				GameBridge.log(Level.WARNING, "An exception occurred while handling packet:", ex);
			}
		}
		
		return true;
	}
	
	public boolean isClosed() {
		return this.socket.isClosed();
	}
	
	public synchronized void close() {
		this.executor.shutdownNow();
		if (!this.socket.isClosed()) this.socket.close();
	}
	
	public boolean isPeerSocketClosed() {
		return this.socket.isClosed();
	}

}
