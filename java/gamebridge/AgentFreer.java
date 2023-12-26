package gamebridge;

import java.lang.ref.WeakReference;
import java.net.DatagramSocket;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;

import org.ice4j.ice.Agent;

public class AgentFreer {
	
	private static Set<Agent> freed = Collections.newSetFromMap(new WeakHashMap<>());
	
	private static Map<DatagramSocket, WeakReference<Agent>> sockets1 = new WeakHashMap<>();
	private static Map<Agent, WeakReference<DatagramSocket>> sockets2 = new WeakHashMap<>();
	
	public static void free(Agent agent) {
		synchronized (agent) {
			if (!freed.add(agent)) return;
			
			try {
				agent.free();
			} catch (Exception ex) {
				GameBridge.log(Level.FINE, "Exception while freeing Ice agent", ex);
			}
			
			var ref = sockets2.remove(agent);
			if (ref == null) return;
			
			var socket = ref.get();
			if (socket != null) {
				
				synchronized (socket) {
					sockets1.remove(socket);
					try {
						socket.close();
					} catch (Exception ex) {
						GameBridge.log(Level.FINE, "Exception while closing Ice DatagramSocket", ex);
					}
				}
			}
			
		}
	}
	
	public static void map(Agent agent, DatagramSocket socket) {
		synchronized (agent) {
			synchronized (socket) {
				sockets1.put(socket, new WeakReference<>(agent));
				sockets2.put(agent, new WeakReference<>(socket));
			}
		}
	}
	
	public static void close(DatagramSocket socket) {
		synchronized (socket) {
			var ref = sockets1.remove(socket);
			if (ref == null) return;
			var agent = ref.get();
			if (agent != null) {
				sockets2.remove(agent);
				free(agent);
			}
			
			try {
				socket.close();
			} catch (Exception ex) {
				GameBridge.log(Level.FINE, "Exception while closing Ice DatagramSocket", ex);
			}
		}
	}

}
