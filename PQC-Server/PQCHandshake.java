/*
	Names: Pedro Malavet
	Description: This is the class for the PQC Handshake
*/

import org.openquantumsafe.KeyEncapsulation;
import org.openquantumsafe.Pair;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.openquantumsafe.KeyEncapsulation;
import org.openquantumsafe.Pair;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.SecureRandom;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public class PQCHandshake 
{

    private static final String ALGORITHM = "ML-KEM-768";

    /*
     * Expected ML-KEM-768 sizes.
     *
     * Public key:    1184 bytes
     * Ciphertext:    1088 bytes
     * Shared secret:   32 bytes
     */
    private static final int PUBLIC_KEY_LENGTH = 1184;
    private static final int CIPHERTEXT_LENGTH = 1088;
    private static final int SHARED_SECRET_LENGTH = 32;


    /*
     * CLIENT SIDE
     *
     * 1. Receive server public key
     * 2. Encapsulate using ML-KEM-768
     * 3. Send ciphertext to server
     * 4. Return client's shared secret
     */
    
	public static HandshakeResult clientHandshake(DataInputStream in,DataOutputStream out,byte[] trustedServerSigningPublicKey) throws Exception 
	{

		//Record Start Time
		long totalHandshakeStart = System.nanoTime();

		System.out.println("[PQC] Starting authenticated "+ "ML-KEM-768 handshake...");

		//Generate a random challenge. This prevents us from simply accepting a replayed old signed handshake.
		byte[] clientNonce = new byte[32];

		java.security.SecureRandom random = new java.security.SecureRandom();
		random.nextBytes(clientNonce);

		// Send challenge to server.
		out.writeInt(clientNonce.length);
		out.write(clientNonce);
		out.flush();
	
		//Receive server's ML-KEM public key.
		int publicKeyLength = in.readInt();

		if (publicKeyLength != 1184) 
		{
			throw new IOException("Invalid ML-KEM-768 public key length: "+ publicKeyLength);
		}


		byte[] kemPublicKey = new byte[publicKeyLength];
		in.readFully(kemPublicKey);

		//Receive ML-DSA signature.
		int signatureLength = in.readInt();

		if (signatureLength <= 0 || signatureLength > 10000) 
		{
			throw new IOException("Invalid ML-DSA signature length");
		}


		byte[] signature = new byte[signatureLength];
		in.readFully(signature);

		//Reconstruct exactly what the server was supposed to have signed.
		byte[] transcript = PQCAuthentication.createServerTranscript(clientNonce,kemPublicKey);

		//Record Start Time
		long mldsaStart = System.nanoTime();

		//Authenticate server using our pre-trusted ML-DSA public key.
		boolean authenticated = PQCAuthentication.verifyServerHandshake(trustedServerSigningPublicKey,transcript,signature);

		//Record End Time
		long mldsaEnd = System.nanoTime();
		long mldsaVerifyNanos = mldsaEnd - mldsaStart;

		if (!authenticated) 
		{
			throw new SecurityException("ML-DSA server authentication failed");
		}


		System.out.println("[PQC] ML-DSA-65 signature verified.");

		System.out.println("[PQC] Server authenticated successfully.");

		//Now that the KEM public key is authenticated, we can safely encapsulate to it.
		KeyEncapsulation kem = new KeyEncapsulation("ML-KEM-768");

		//Record Start Time
		long kemStart = System.nanoTime();
		
		Pair<byte[], byte[]> pair = kem.encap_secret(kemPublicKey);
		
		//Recode End Time
		long kemEnd = System.nanoTime();
		long kemEncapsulationNanos = kemEnd - kemStart;
		
		byte[] ciphertext = pair.getLeft();
		byte[] sharedSecret = pair.getRight();

		out.writeInt(ciphertext.length);
		out.write(ciphertext);
		out.flush();
		
		System.out.println("[PQC] ML-KEM ciphertext sent.");
		
		//Build Transcript Hash
		byte[] transcriptHash = createTranscriptHash(clientNonce,kemPublicKey,signature,ciphertext);

		System.out.println("[PQC] Handshake transcript hashed.");
		System.out.println("[PQC] Authenticated handshake complete.");
		
		//Record End Time
		long totalHandshakeEnd = System.nanoTime();
		long totalHandshakeNanos = totalHandshakeEnd - totalHandshakeStart;

		return new HandshakeResult(sharedSecret,transcriptHash,mldsaVerifyNanos,0,kemEncapsulationNanos,0,totalHandshakeNanos);
		
	}


    /*
     * SERVER SIDE
     *
     * 1. Generate ML-KEM-768 keypair
     * 2. Send public key to client
     * 3. Receive ciphertext
     * 4. Decapsulate ciphertext
     * 5. Return server's shared secret
     */
    public static HandshakeResult serverHandshake(DataInputStream in,DataOutputStream out,byte[] serverSigningSecretKey) throws Exception 
	{
		//Record Total Start Time
		long totalHandshakeStart = System.nanoTime();

		System.out.println("[PQC] Starting authenticated " + "server handshake...");

		//Receive client's random challenge.
		int nonceLength = in.readInt();

		if (nonceLength != 32) 
		{
			throw new IOException("Invalid client nonce length");
		}

		byte[] clientNonce = new byte[nonceLength];
		in.readFully(clientNonce);


		//Generate ephemeral ML-KEM-768 keypair.
		KeyEncapsulation kem = new KeyEncapsulation("ML-KEM-768");
		
		//Record Start Time
		long kemKeygenStart = System.nanoTime();
		
		byte[] kemPublicKey = kem.generate_keypair();
		
		//Record End Time
		long kemKeygenEnd =  System.nanoTime();
		long kemKeygenNanos = kemKeygenEnd - kemKeygenStart;

		//Build authenticated transcript.
		byte[] transcript = PQCAuthentication.createServerTranscript(clientNonce,kemPublicKey);

		//Record Start Time
		long mldsaStart = System.nanoTime();

		//Sign it using the server's persistent ML-DSA-65 identity key.
		byte[] signature = PQCAuthentication.signServerHandshake(serverSigningSecretKey,transcript);
		
		//Record End Time
		long mldsaEnd = System.nanoTime();
		long mldsaSigningNanos = mldsaEnd - mldsaStart;

		System.out.println("[PQC] Handshake signed with ML-DSA-65.");


		//Send authenticated KEM public key.
		out.writeInt(kemPublicKey.length);
		out.write(kemPublicKey);

		//Send corresponding ML-DSA signature.
		out.writeInt(signature.length);
		out.write(signature);
		out.flush();

		//Receive KEM ciphertext.
		int ciphertextLength = in.readInt();

		if (ciphertextLength != 1088) 
		{
			throw new IOException("Invalid ML-KEM-768 ciphertext length");
		}


		byte[] ciphertext = new byte[ciphertextLength];
		in.readFully(ciphertext);

		//Recover shared secret.
		//Record Start Time
		long kemDecapStart = System.nanoTime();
		byte[] sharedSecret = kem.decap_secret(ciphertext);
		
		//Record End Time
		long kemDecapEnd = System.nanoTime();
		long kemDecapsulationNanos = kemDecapEnd - kemDecapStart;
		
		
		byte[] transcriptHash = createTranscriptHash(clientNonce,kemPublicKey,signature,ciphertext);
		
		System.out.println("[PQC] Handshake transcript hashed.");
		System.out.println("[PQC] Authenticated " + "ML-KEM handshake complete.");
		
		//Record Total End Time
		long totalHandshakeNanos = System.nanoTime() - totalHandshakeStart;

		return new HandshakeResult(sharedSecret,transcriptHash,mldsaSigningNanos,kemKeygenNanos,0,kemDecapsulationNanos,totalHandshakeNanos);
		
		
	}
	
	
	private static void writeField(DataOutputStream out,byte[] data) 
	throws Exception 
	{
		out.writeInt(data.length);
		out.write(data);
	}
	
	private static byte[] createTranscriptHash(byte[] clientNonce,byte[] kemPublicKey,byte[] signature,byte[] kemCiphertext) throws Exception 
	{

		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		DataOutputStream transcript = new DataOutputStream(buffer);

		//Protocol and algorithm identifiers prevent this transcript from accidentally being interpreted as belonging to another protocol.
		writeField(transcript,"PQC-FTP-HANDSHAKE-V2".getBytes(StandardCharsets.UTF_8));
		writeField(transcript,"ML-DSA-65".getBytes(StandardCharsets.UTF_8));
		writeField(transcript,"ML-KEM-768".getBytes(StandardCharsets.UTF_8));

		//Actual handshake messages.
		writeField(transcript,clientNonce);
		writeField(transcript,kemPublicKey);
		writeField(transcript,signature);
		writeField(transcript,kemCiphertext);

		transcript.flush();

		MessageDigest digest = MessageDigest.getInstance("SHA-256");

		return digest.digest(buffer.toByteArray());
	}
	
	public static class HandshakeResult 
	{

		private final byte[] sharedSecret;
		private final byte[] transcriptHash;

		private final long mldsaNanos;

		private final long mlkemKeyGenerationNanos;
		private final long mlkemEncapsulationNanos;
		private final long mlkemDecapsulationNanos;

		private final long totalHandshakeNanos;


		public HandshakeResult(byte[] sharedSecret,byte[] transcriptHash,long mldsaNanos,long mlkemKeyGenerationNanos,long mlkemEncapsulationNanos,
				long mlkemDecapsulationNanos,long totalHandshakeNanos) 
		{

			this.sharedSecret = sharedSecret.clone();
			this.transcriptHash = transcriptHash.clone();
			this.mldsaNanos = mldsaNanos;
			this.mlkemKeyGenerationNanos = mlkemKeyGenerationNanos;
			this.mlkemEncapsulationNanos = mlkemEncapsulationNanos;
			this.mlkemDecapsulationNanos = mlkemDecapsulationNanos;
			this.totalHandshakeNanos = totalHandshakeNanos;
		}

		public byte[] getSharedSecret() 
		{
			return sharedSecret.clone();
		}

		public byte[] getTranscriptHash()
		{
			return transcriptHash.clone();
		}

		public long getMldsaNanos()
		{
			return mldsaNanos;
		}

		public long getMlkemKeyGenerationNanos()
		{
			return mlkemKeyGenerationNanos;
		}

		public long getMlkemEncapsulationNanos()
		{
			return mlkemEncapsulationNanos;
		}

		public long getMlkemDecapsulationNanos()
		{
			return mlkemDecapsulationNanos;
		}

		public long getTotalHandshakeNanos() 
		{
			return totalHandshakeNanos;
		}
	}
}