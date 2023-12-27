package gamebridge;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Agent;
import org.ice4j.ice.Component;
import org.ice4j.ice.IceProcessingState;
import org.ice4j.ice.KeepAliveStrategy;
import org.ice4j.ice.harvest.StunCandidateHarvester;

public class Ice {
	
	private static String[] stunServers = {
			"stun.l.google.com:19302",
			"stun1.l.google.com:19302",
			"stun2.l.google.com:19302",
			"stun3.l.google.com:19302",
			"stun4.l.google.com:19302"
	};
	
	private void addStunServers() {
		for (String server : stunServers) {
			try {
				String[] split = server.split(":");
				int port = Integer.parseInt(split[1]);
				var address = InetAddress.getByName(split[0]);
				var ta = new TransportAddress(address, port, Transport.UDP);
				var harvester = new StunCandidateHarvester(ta);
				agent.addCandidateHarvester(harvester);
			} catch (Exception ex) {
				GameBridge.log(Level.WARNING, "Exception while adding stun servers", ex);
			}
		}
	}
	
	private Agent agent;
	private String sdp;
	
	public Ice() throws Exception {
		try {
			agent = new Agent();
			if (GameBridge.getLoggingLevel().intValue() > Level.FINE.intValue()) {
				agent.setLoggingLevel(Level.OFF);
			}
			agent.addStateChangeListener(new Listener());
			agent.setUseDynamicPorts(true);
			var stream = agent.createMediaStream("data");
			agent.createComponent(stream, KeepAliveStrategy.SELECTED_ONLY, false);
			addStunServers();
			sdp = SdpUtils.createSDPDescription(agent);
		} catch (Exception ex) {
			GameBridge.log(Level.WARNING, "Exception while creating Ice: ", ex);
			agent.free();
			throw ex;
		}
	}
	
	public String getLocalSDP() {
		return sdp;
	}
	
	private CompletableFuture<IceSocket> future = new CompletableFuture<IceSocket>();
	
	public void setRemoteSDP(String remoteSDP) {
		future.orTimeout(60, TimeUnit.SECONDS);
		try {
			SdpUtils.parseSDP(this.agent, remoteSDP);
		} catch (Exception ex) {
			GameBridge.log(Level.WARNING, "Exception while setting remote SDP: ", ex);
			this.agent.free();
			future.completeExceptionally(ex);
			return;
		}
		this.agent.startConnectivityEstablishment();
	}
	
	public CompletableFuture<IceSocket> establish(int timeout, TimeUnit unit) {
		future.orTimeout(timeout, unit);
		future.exceptionally(ex -> {
			this.agent.free();
			return null;
		});
		return future;
	}
	
	public CompletableFuture<IceSocket> establish() {
		return future;
	}
	
	private InetSocketAddress remote;
	
	public InetSocketAddress getRemote() {
		return remote;
	}
	
	private class Listener implements PropertyChangeListener {

		@Override
		public void propertyChange(PropertyChangeEvent event) {
			var state = (IceProcessingState)event.getNewValue();
			if (state == IceProcessingState.FAILED) {
				future.completeExceptionally(new RuntimeException("Ice processing failed"));
				AgentFreer.free(agent);
				return;
			}
			if (state != IceProcessingState.TERMINATED) return;
			var rtp = agent.getStream("data").getComponent(Component.RTP);
			if (rtp == null) {
				AgentFreer.free(agent);
				future.completeExceptionally(new RuntimeException("Ice processing failed (rtp is null)"));
				return;
			}
			var pair = rtp.getSelectedPair();
			if (pair == null) {
				AgentFreer.free(agent);
				future.completeExceptionally(new RuntimeException("Ice processing failed (pair is null)"));
				return;
			}
			@SuppressWarnings("deprecation")
			var socket = pair.getDatagramSocket();
			if (socket == null) {
				AgentFreer.free(agent);
				future.completeExceptionally(new RuntimeException("Ice processing failed (socket is null)"));
				return;
			}
			
			AgentFreer.map(agent, socket);
			
			GameBridge.log(Level.INFO, "Ice connection was a success");
			
			var remoteAddress = pair.getRemoteCandidate().getHostAddress().getAddress();
			var remotePort = pair.getRemoteCandidate().getHostAddress().getPort();
			remote = new InetSocketAddress(remoteAddress, remotePort);
			future.complete(new IceSocket(socket, remote));
		}
		
	}
	
	
	public void close() {
		AgentFreer.free(agent);
	}

}
