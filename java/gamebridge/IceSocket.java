package gamebridge;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.logging.Level;

public class IceSocket {
	
	private DatagramSocket socket;
	
	public IceSocket(DatagramSocket socket, InetAddress remoteAddress, int remotePort) {
		this.socket = socket;
		this.sendPacket = new DatagramPacket(sendBytes, mtu, remoteAddress, remotePort);
	}
	
	public IceSocket(DatagramSocket socket, InetSocketAddress remote) {
		this(socket, remote.getAddress(), remote.getPort());
	}

	private static final int mtu = 1400;
	private byte[] sendBytes = new byte[mtu];
	private DatagramPacket sendPacket;
	
	public void send(byte[] bytes) throws IOException {
		send(bytes, 0, bytes.length);
	}
	
	public void send(byte[] bytes, int off, int length) throws IOException {
		if (length <= mtu-1) {
			sendBytes[0] = 1;
			System.arraycopy(bytes, off, sendBytes, 1, length);
			sendPacket.setLength(length+1);
			GameBridge.log(Level.FINEST, "Sending " + length);
			socket.send(sendPacket);
			GameBridge.log(Level.FINEST, "Sent " + length);
		} else {
			int remaining = length;
			int index = off;
			int sent = 1;
			while (remaining > 0) {
				int r = Math.min(remaining, mtu-1);
				sendBytes[0] = (r == remaining) ? (byte)1 : 0;
				GameBridge.log(Level.FINEST, sent + " Sending " + r);
				System.arraycopy(bytes, index, sendBytes, 1, r);
				index += r;
				remaining -= r;
				sent += 1;
				sendPacket.setLength(r+1);
				GameBridge.log(Level.FINEST, "Sending " + r);
				socket.send(sendPacket);
				GameBridge.log(Level.FINEST, "Sent " + r);
			}
			GameBridge.log(Level.FINEST, "Done sending");
		}
	}
	
	private byte[] receiveBytes = new byte[mtu];
	private DatagramPacket receivePacket = new DatagramPacket(receiveBytes, mtu);
	
	public void receiveInternal() throws IOException {
		socket.receive(receivePacket);
	}
	
	public int receive(byte[] bytes) throws IOException {
		socket.receive(receivePacket);
		GameBridge.log(Level.FINEST, "Receive");
		int length = receivePacket.getLength() - 1;
		if (receiveBytes[0] == 1) {
			GameBridge.log(Level.FINEST, "Receiving " + length);
			System.arraycopy(receiveBytes, 1, bytes, 0, length);
			return length;
		} else {
			int totalLength = 0;
			int index = 0;
			int received = 1;
			while (true) {
				GameBridge.log(Level.FINEST, received + " Receiving " + length);
				System.arraycopy(receiveBytes, 1, bytes, index, length);
				index += length;
				totalLength += length;
				if (receiveBytes[0] == 1) break;
				socket.receive(receivePacket);
				received += 1;
				length = receivePacket.getLength() - 1;
			}
			return totalLength;
		}
	}
	
	private boolean closed;
	
	public void close() {
		closed = true;
		AgentFreer.close(socket);
	}

	public boolean isClosed() {
		if (closed) return true;
		if (this.socket.isClosed()) {
			close();
			return true;
		}
		return false;
	}
}
