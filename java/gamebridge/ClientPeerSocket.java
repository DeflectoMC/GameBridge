package gamebridge;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;

public class ClientPeerSocket extends BasicPeerSocket {

	private ServerSocket tcpProxy;
	private Socket tcpSocket;

	@Override
	public void close() {
		if (this.tcpSocket != null) {
			try {
				this.tcpSocket.close();
			} catch (IOException ex) {
				GameBridge.log(Level.WARNING, "Error while closing socket:", ex);
			}
		}
		try {
			this.tcpProxy.close();
		} catch (IOException ex) {
			GameBridge.log(Level.WARNING, "Error while closing socket handler:", ex);
		}
		super.close();
	}

	@Override
	public void start() {
		this.executor.submit(this::connectLoop);
		super.start();
	}

	private final Object queueLock = new Object();

	private volatile boolean opened = false;

	private void connectLoop() {
		while (!tcpProxy.isClosed() && !this.isPeerSocketClosed()) {
			Socket tcpSocket;
			try {
				tcpSocket = tcpProxy.accept();
			} catch (InterruptedIOException ex) {
				return;
			} catch (IOException ex) {
				if (!tcpProxy.isClosed()) GameBridge.log(Level.WARNING, "Error while accepting socket:", ex);
				continue;
			}
			if (tcpProxy.isClosed() || this.isPeerSocketClosed()) return;
			this.tcpSocket = tcpSocket;
			synchronized (this.queueLock) {
				var ex0 = this.send("tcpOpen");
				if (ex0 instanceof InterruptedIOException) return;
				if (!opened) {
					try {
						this.queueLock.wait(10000l);
					} catch (InterruptedException ex) {
						return;
					}
				}
				if (!opened) {
					GameBridge.log(Level.WARNING, "[Client] Host never opened");
					try {
						tcpSocket.close();
					} catch (IOException ex) {}
					continue;
				}
			}
			GameBridge.info("[Client] Host opened");
			if (tcpSocket.isClosed() || tcpProxy.isClosed() || this.isPeerSocketClosed()) continue;
			if (this.sendLoop(tcpSocket) instanceof InterruptedIOException) {
				return;
			}
		}
		if (!tcpProxy.isClosed()) {
			try {
				tcpProxy.close();
			} catch (Exception ex) {}
		}
		GameBridge.log(Level.FINE, "connectLoop ended");
	}

	public InterruptedIOException sendLoop(Socket tcpSocket) {

		GameBridge.info("[Client] Sending queued packets");

		byte[] bytes = new byte[1 + (2 * 1024 * 1024)];
		while (!tcpProxy.isClosed() && !tcpSocket.isClosed() && !this.isPeerSocketClosed()) {
			int read;
			try {
				read = tcpSocket.getInputStream().read(bytes, 1, bytes.length-1);
			} catch (InterruptedIOException ex) {
				return ex;
			} catch (IOException ex) {
				if (!tcpProxy.isClosed() && !tcpSocket.isClosed() && !this.isPeerSocketClosed()) {
					GameBridge.log(Level.WARNING, "Error while translating TCP packet: ", ex);
				} else if (!this.isPeerSocketClosed()) {
					GameBridge.info("[Client] TCP Closed");
					var ex2 = this.send("tcpClose");
					if (ex2 instanceof InterruptedIOException) return (InterruptedIOException)ex2;
				}
				continue;
			}
			if (read == -1 && !this.isPeerSocketClosed()) {
				GameBridge.info("[Client] TCP Close");
				var ex = this.send("tcpClose");
				if (ex instanceof InterruptedIOException) return (InterruptedIOException)ex;
				return null;
			}

			GameBridge.log(Level.FINEST, "[Client] Sending TCP Packet: " + read);
			var ex = this.send("tcpPacket", bytes, 1, read);
			if (ex instanceof InterruptedIOException) return (InterruptedIOException)ex;
		}
		return null;
	}
	
	private String hostID;
	
	public String getServerID() {
		return hostID;
	}

	public ClientPeerSocket(int tcpPort, IceSocket socket, String hostID) throws IOException {
		super(socket);
		this.hostID = hostID;

		try {
			tcpProxy = new ServerSocket(tcpPort);
		} catch (IOException ex) {
			socket.close();
			throw ex;
		}



		this.registerChannel("tcpOpen", 0, () -> {
			GameBridge.info("[Client] Received TCP Open");
			synchronized (queueLock) {
				GameBridge.log(Level.FINER, "[Client] Synchronized");
				opened = true;
				queueLock.notifyAll();
			}
		});

		this.registerChannel("tcpPacket", 1, (packet) -> {
			if (tcpSocket == null) {
				this.send("tcpClose");
				return;
			}
			byte[] bytes = packet.array();
			int off = packet.position();
			int len = packet.remaining();
			try {
				GameBridge.log(Level.FINEST, "[Client] Received TCP");
				this.tcpSocket.getOutputStream().write(bytes, off, len);
			} catch (IOException ex) {
				if (this.tcpSocket.isClosed()) {
					this.send("tcpClose");
					return;
				}
				GameBridge.log(Level.WARNING, "Error while receiving TCP packet:", ex);
			}
		});

		this.registerChannel("tcpClose", 2, () -> {
			GameBridge.log(Level.FINER, "[Client] Received TCP close");
			if (tcpSocket == null) return;
			if (this.tcpSocket.isClosed()) return;
			try {
				this.tcpSocket.close();
			} catch (IOException ex) {
				GameBridge.log(Level.WARNING, "Error while closing socket:", ex);
			}
		});
	}

}
