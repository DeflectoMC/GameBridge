package gamebridge.nexus;

import java.nio.ByteBuffer;
import java.util.Base64;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.jcajce.provider.digest.SHA3;

public class Login {
	
	public static byte[] hash(byte[]... bytes) {
		var hash = new SHA3.Digest512();
		for (var array : bytes) {
			hash.update(array);
		}
		return hash.digest();
	}
	
	/*
	public static String getPublicID(byte[] privKey) {
		var privateKey = new Ed25519PrivateKeyParameters(privKey);
		var publicKey = privateKey.generatePublicKey();
		var pubKey = publicKey.getEncoded();
		var string = Base64.getEncoder().encodeToString(hash(pubKey));
		string = string.replaceAll("[A-Z=+/-]", "");
		if (string.length() > 32)
			string = string.substring(0, 32);
		return string;
	}*/
	
	public static String getBase64URL(byte[] bytes) {
		var string = Base64.getEncoder().encodeToString(bytes);
		return string.replace('+', '-').replace('/', '_');
	}
	
	public static byte[] generate(byte[] privKey, int preferredLength) throws IllegalArgumentException {
		if (preferredLength < 11) throw new IllegalArgumentException("Preferred ID length cannot be less than 11");
		if (preferredLength > 50) throw new IllegalArgumentException("Preferred ID length cannot be greater than 50");
		
		
		var privateKey = new Ed25519PrivateKeyParameters(privKey);
		var publicKey = privateKey.generatePublicKey();
		byte[] time = new byte[8];
		ByteBuffer.wrap(time).putLong(System.currentTimeMillis());
		
		
		var sig = new Ed25519Signer();
		
		sig.init(true, new Ed25519PrivateKeyParameters(privKey, 0));
		sig.update(time, 0, 8);
		
		var signature = sig.generateSignature();
		
		var result = ByteBuffer.allocate(3 + 8 + 32 + 64)
	    .put((byte)0)
	    .put((byte)1)
	    .put((byte)preferredLength)
	    .put(time)
	    .put(publicKey.getEncoded())
	    .put(signature)
	    .array();
		
		try {
			verify(result);
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("Invalid ID");
		}
		
		return result;
	}
	
	public static void verify(byte[] login) throws IllegalArgumentException {
		var buf = ByteBuffer.wrap(login);
		if (buf.get() != 0) throw new IllegalArgumentException("Wrong protocol");
		if (buf.get() != 1) throw new IllegalArgumentException("Couldn't recognize encryption scheme");
		int preferredLength = Byte.toUnsignedInt(buf.get());
		if (preferredLength < 11) throw new IllegalArgumentException("Preferred length is less than 11");
		if (preferredLength > 50) throw new IllegalArgumentException("Preferred length is greater than 50");
		if (login.length != 3 + 8 + 32 + 64) throw new IllegalArgumentException("Wrong login length");
		byte[] time = new byte[8];
		buf.get(time);
		if (ByteBuffer.wrap(time).getLong() <= System.currentTimeMillis() - 60_000) {
			throw new IllegalArgumentException("Signature expired");
		}
		if (ByteBuffer.wrap(time).getLong() >= System.currentTimeMillis() + 60_000) {
			throw new IllegalArgumentException("Signature is too early");
		}
		var pubKey = new byte[32];
		var signature = new byte[64];
		buf.get(pubKey);
		buf.get(signature);
		
		var publicKey = new Ed25519PublicKeyParameters(pubKey);
		
		var sig = new Ed25519Signer();
		sig.init(false, publicKey);
		sig.update(time, 0, 8);
		if (!sig.verifySignature(signature))
			throw new IllegalArgumentException("Failed to verify signature");
		
		//Verified
		return;
	}

}
