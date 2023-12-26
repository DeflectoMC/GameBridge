package gamebridge.nexus;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.jcajce.provider.digest.SHA3;
import org.bouncycastle.util.Arrays;

public class Crypto {
	

	public static Ed25519PrivateKeyParameters getPrivateKey(UUID playerUUID) {
		var unique = System.getenv("COMPUTERNAME")
				+ System.getProperty("user.name")
				+ System.getenv("PROCESSOR_IDENTIFIER")
				+ System.getenv("PROCESSOR_LEVEL");
		
		var result = hash(unique, "GameBridge player", playerUUID.toString());
		return new Ed25519PrivateKeyParameters(Arrays.copyOfRange(result, 0, 32));
	}
	
	public static Ed25519PrivateKeyParameters getPrivateKey(String serverKey) {
		var unique = System.getenv("COMPUTERNAME")
				+ System.getProperty("user.name")
				+ System.getenv("PROCESSOR_IDENTIFIER")
				+ System.getenv("PROCESSOR_LEVEL");
		var result = hash(unique, "GameBridge server", serverKey);
		return new Ed25519PrivateKeyParameters(Arrays.copyOfRange(result, 0, 32));
	}
	
	public static Ed25519PrivateKeyParameters getPrivateKey(byte[] seed) {
		var unique = System.getenv("COMPUTERNAME")
				+ System.getProperty("user.name")
				+ System.getenv("PROCESSOR_IDENTIFIER")
				+ System.getenv("PROCESSOR_LEVEL");
		var result = hash(unique.getBytes(StandardCharsets.UTF_8), seed);
		return new Ed25519PrivateKeyParameters(Arrays.copyOfRange(result, 0, 32));
	}
	
	public static Ed25519PublicKeyParameters getPublicKey(UUID playerUUID) {
		return getPrivateKey(playerUUID).generatePublicKey();
	}
	
	public static Ed25519PublicKeyParameters getPublicKey(String serverKey) {
		return getPrivateKey(serverKey).generatePublicKey();
	}
	
	public static Ed25519PublicKeyParameters getPublicKey(byte[] seed) {
		return getPrivateKey(seed).generatePublicKey();
	}
	
	public static byte[] hash(String... strings) {
		byte[][] bytes = new byte[strings.length][];
		for (int i = 0; i < strings.length; i++) {
			if (strings[i] == null) {
				bytes[i] = null;
				continue;
			}
			bytes[i] = strings[i].getBytes(StandardCharsets.UTF_8);
		}
		return hash(bytes);
	}

	public static byte[] hash(byte[]... bytes) {
		var hash = new SHA3.Digest512();
		for (var array : bytes) {
			if (array == null) array = new byte[0];
			var hash2 = new SHA3.Digest512();
			hash2.update(array);
			hash.update(hash2.digest());
		}
		return hash.digest();
	}

}
