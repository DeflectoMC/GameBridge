package gamebridge;

import org.jitsi.metaconfig.ConfigSource;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import kotlin.jvm.functions.Function1;
import kotlin.reflect.KType;

public class GameBridgeConfigSource implements ConfigSource {
	
	public static final String reference_conf = "ice4j {\n"
			+ "   ice {\n"
			+ "     max-check-list-size = 100\n"
			+ "     // The amount of time that we must wait after ICE processing enters a COMPLETED state before we free candidates\n"
			+ "     // and move into the TERMINATED state.\n"
			+ "     termination-delay = 3 seconds\n"
			+ "   }\n"
			+ "\n"
			+ "  // The value of the SOFTWARE attribute that ice4j should include in all outgoing messages. Set to the empty string to\n"
			+ "  // not include a SOFTWARE attribute.\n"
			+ "  software = \"ice4j.org\"\n"
			+ "\n"
			+ "  // Whether the per-component merging socket should be enabled by default (the default value can be\n"
			+ "  // overridden with the [Agent] API).\n"
			+ "  // If enabled, the user of the library must use the socket instance provided by [Component.getSocket]. Otherwise,\n"
			+ "  // the socket instance from the desired [CandidatePair] must be used.\n"
			+ "  use-component-socket = true\n"
			+ "\n"
			+ "  consent-freshness {\n"
			+ "    // How often a STUN Binding request used for consent freshness check will be sent.\n"
			+ "    interval = 15 seconds\n"
			+ "    // The amount of time without a valid STUN Binding response after which a STUN Binding request is to be\n"
			+ "    // retransmitted according to `STUN Usage for Consent Freshness` (RFC7675).\n"
			+ "    // This is the original value for back-off strategy, while `max-wait-interval` sets the upper limit.\n"
			+ "    original-wait-interval = 500 milliseconds\n"
			+ "    // The amount of time without a valid STUN Binding response after which a STUN Binding request is to be\n"
			+ "    // retransmitted according to `STUN Usage for Consent Freshness` (RFC7675).\n"
			+ "    // This is the final value for the back-off strategy, while `original-wait-interval` defines the initial interval for\n"
			+ "    // the first request sent.\n"
			+ "    max-wait-interval = 500 milliseconds\n"
			+ "    // The maximum number of retransmissions of a STUN Binding request without a valid STUN Binding response after which\n"
			+ "    // consent freshness is to be considered unconfirmed according to `STUN Usage for Consent Freshness` (RFC7675).\n"
			+ "    max-retransmissions = 30\n"
			+ "    // Whether to randomize the period between any two checks between 0.8 and 1.2 of the configured interval as\n"
			+ "    // recommended in RFC7675 Section 5.1. We keep this configurable in case the previous behavior is desired.\n"
			+ "    randomize-interval = true\n"
			+ "  }\n"
			+ "\n"
			+ "  // Configuration related to harvesting (aka gathering) of local candidates.\n"
			+ "  harvest {\n"
			+ "    // Whether to harvest IPv6 addresses.\n"
			+ "    use-ipv6 = true\n"
			+ "    // Whether to use link-local addresses when harvesting candidates.\n"
			+ "    use-link-local-addresses = true\n"
			+ "\n"
			+ "    // How long to wait for an individual harvest before timing out.\n"
			+ "    timeout = 15 seconds\n"
			+ "\n"
			+ "    // Configuration for the \"single port\" UDP harvester.\n"
			+ "    udp {\n"
			+ "      // The size in bytes to set on the UDP socket (SO_RCVBUF). If not specified, the default from the OS will be used.\n"
			+ "      #receive-buffer-size = 10485760\n"
			+ "\n"
			+ "      // Whether to allocate ephemeral ports for local candidates. This is the default value, and can be overridden\n"
			+ "      // for Agent instances.\n"
			+ "      use-dynamic-ports = true\n"
			+ "    }\n"
			+ "\n"
			+ "    // Configuration for the set of \"mapping\" harvesters.\n"
			+ "    mapping {\n"
			+ "      aws {\n"
			+ "        enabled = true\n"
			+ "        // Whether to use the AWS harvester even when the automatic detection indicates that we are not running in AWS.\n"
			+ "        force = false\n"
			+ "      }\n"
			+ "      stun {\n"
			+ "        addresses = [ ]\n"
			+ "        # addresses = [ \"stun1.example.com:5379\", \"stun2.example.com:443\" ]\n"
			+ "      }\n"
			+ "      // Pre-configured mappings\n"
			+ "      static-mappings = [\n"
			+ "        #{\n"
			+ "        #    // This results in a candidate for 1.2.3.4:20000 being added whenever a host candidate for\n"
			+ "        #    // 10.10.0.123:10000 exists.\n"
			+ "        #    local-address = \"10.10.0.123\"\n"
			+ "        #    public-address = \"1.2.3.4\"\n"
			+ "        #    // The ports are optional, but need to either both be provided or both not provided.\n"
			+ "        #    local-port = 10000\n"
			+ "        #    public-port = 20000\n"
			+ "        #    // Optional name\n"
			+ "        #    name = \"my-mapping-for-a-specific-port\"\n"
			+ "        #},\n"
			+ "        #{\n"
			+ "        #    // This results in a candidate for 1.2.3.4:PORT being added whenever a host candidate for 10.10.0.123\n"
			+ "        #    // exists, where the PORT is the port of the existing host candidate.\n"
			+ "        #    local-address = \"10.10.0.123\"\n"
			+ "        #    public-address = \"1.2.3.4\"\n"
			+ "        #    // Optional name\n"
			+ "        #    name = \"my-mapping-for-all-ports\"\n"
			+ "        #}\n"
			+ "      ]\n"
			+ "    }\n"
			+ "  }\n"
			+ "}";
	
	private Config config;

	public GameBridgeConfigSource() {
		this.config = ConfigFactory.parseString(reference_conf);
	}

	@Override
	public String getDescription() {
		return "GameBridge jitsi config";
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "GameBridgeConfigSource";
	}

	@Override
	public Function1<String, Object> getterFor(KType arg0) {
		
		return (name -> {
			return config.getAnyRef(name);
		});
	}

}
