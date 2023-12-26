package gamebridge;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.Socket;
import java.util.logging.Level;

public class HostPeerSocket extends BasicPeerSocket {

	private Socket tcpSocket;

	@Override
	public void close() {
		if (tcpSocket != null) try {
			tcpSocket.close();
		} catch (IOException ex) {}
		super.close();
	}

	private void sendLoop() {
		var tcpSocket = this.tcpSocket;
		var sendBytes = new byte[1 + (2 * 1024 * 1024)];
		while (!tcpSocket.isClosed() && !this.isPeerSocketClosed()) {
			int read;
			try {
				read = tcpSocket.getInputStream().read(sendBytes, 1, sendBytes.length-1);
			} catch (InterruptedIOException ex) {
				return;
			}
			catch (IOException ex) {
				if (!tcpSocket.isClosed()) GameBridge.log(Level.WARNING, "Error while translating TCP packet:", ex);
				continue;
			}
			if (read == -1) {
				break;
			}
			GameBridge.log(Level.FINEST, "[Host] Sending TCP");
			if (this.send("tcpPacket", sendBytes, 1, read) instanceof InterruptedIOException) return;
		}
		GameBridge.log(Level.FINEST, "[Host] Closed TCP");
		this.send("tcpClose");
	}

	public HostPeerSocket(int tcpPort, IceSocket socket) {
		super(socket);
		this.registerChannel("tcpOpen", 0, () -> {
			Socket tcpSocket;
			try {
				tcpSocket = new Socket("localhost", tcpPort);
			} catch (Exception ex) {
				GameBridge.log(Level.WARNING, "Failed to connect to server running at localhost:" + tcpPort);
				this.send("tcpClose");
				return;
			}
			this.tcpSocket = tcpSocket;
			this.executor.submit(this::sendLoop);
			GameBridge.log(Level.FINER, "[Host] Opened TCP");
			this.send("tcpOpen");
			GameBridge.log(Level.FINER, "[Host] Sent TCP Open");
		});

		this.registerChannel("tcpPacket", 1, (packet) -> {
			if (tcpSocket == null) {
				GameBridge.log(Level.FINER, "[Host] Closed TCP");
				this.send("tcpClose");
				return;
			}
			GameBridge.log(Level.FINEST, "[Host] Received TCP");
			byte[] bytes = packet.array();
			int off = packet.position();
			int len = packet.remaining();
			try {
				this.tcpSocket.getOutputStream().write(bytes, off, len);
			} catch (IOException ex) {
				if (this.tcpSocket.isClosed()) {
					this.send("tcpClose");
					return;
				}
				GameBridge.log(Level.WARNING, "Error while translating TCP packet", ex);
			}
		});

		this.registerChannel("tcpClose", 2, () -> {
			if (tcpSocket == null) {
				GameBridge.log(Level.FINER, "[Host] Closed TCP");
				this.send("tcpClose");
				return;
			}
			if (this.tcpSocket.isClosed()) return;
			try {
				this.tcpSocket.close();
			} catch (IOException ex) {
				GameBridge.log(Level.WARNING, "Error while closing TCP socket", ex);
			}
			GameBridge.log(Level.FINER, "[Host] Closed TCP");
			this.send("tcpClose");
		});
	}

}
