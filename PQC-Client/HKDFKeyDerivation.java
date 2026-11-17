/*
	Names: Pedro Malavet
	Description: This is the class for The HKDF Key Derivation
*/

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

public class HKDFKeyDerivation 
{

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private static final int HASH_LENGTH = 32;

	//AES-256 requires 32 bytes.
    private static final int AES_KEY_LENGTH = 32;

	//Derive all four traffic keys from the. ML-KEM shared secret.
    public static SessionKeys deriveSessionKeys(byte[] sharedSecret, byte[] transcriptHash) throws Exception 
	{

        if (sharedSecret == null || sharedSecret.length != 32) 
		{
            throw new IllegalArgumentException("ML-KEM shared secret must be 32 bytes");
        }

		if (transcriptHash == null || transcriptHash.length != 32) 
		{
			throw new IllegalArgumentException("SHA-256 transcript hash must be 32 bytes");
		}

		//HKDF-Extract Produces a pseudorandom key (PRK).
        byte[] prk = hkdfExtract(transcriptHash,sharedSecret);


		//HKDF-Expand with a different "info" string for every purpose.
        byte[] commandClientToServer = hkdfExpand(prk,"PQC-FTP-v2 command client-to-server",AES_KEY_LENGTH);
        byte[] commandServerToClient = hkdfExpand(prk,"PQC-FTP-v2 command server-to-client",AES_KEY_LENGTH);
        byte[] fileClientToServer = hkdfExpand(prk,"PQC-FTP-v2 file client-to-server",AES_KEY_LENGTH);
        byte[] fileServerToClient = hkdfExpand(prk,"PQC-FTP-v2 file server-to-client",AES_KEY_LENGTH);


		//We no longer need the intermediate PRK.
        Arrays.fill(prk, (byte) 0);

        return new SessionKeys(commandClientToServer,commandServerToClient,fileClientToServer,fileServerToClient);
    }

	//HKDF-Extract: PRK = HMAC(salt, input key material)
    private static byte[] hkdfExtract(byte[] salt,byte[] inputKeyMaterial) throws Exception 
	{

        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec saltKey = new SecretKeySpec(salt,HMAC_ALGORITHM);
        mac.init(saltKey);

        return mac.doFinal(inputKeyMaterial);
    }

	//HKDF-Expand.
    private static byte[] hkdfExpand(byte[] prk,String infoString,int outputLength) throws Exception
	{

        if (outputLength <= 0 || outputLength > 255 * HASH_LENGTH) 
		{

            throw new IllegalArgumentException("Invalid HKDF output length");
        }


        byte[] info = infoString.getBytes(StandardCharsets.UTF_8);


        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(prk,HMAC_ALGORITHM));

        byte[] output = new byte[outputLength];
        byte[] previous = new byte[0];

        int offset = 0;
        int counter = 1;

        while (offset < outputLength) 
		{

            mac.reset();

            mac.update(previous);
            mac.update(info);
            mac.update((byte) counter);

            previous = mac.doFinal();


            int bytesToCopy = Math.min(previous.length,outputLength - offset);


            System.arraycopy(previous,0,output,offset,bytesToCopy);

            offset += bytesToCopy;
            counter++;
        }


        Arrays.fill(previous, (byte) 0);

        return output;
    }


    private static byte[] sha256(byte[] input) 
	{
        try 
		{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
        }
        catch (Exception e) 
		{
            throw new RuntimeException(e);
        }
    }

	//For Debugging HKDF only
	public static String fingerprint(byte[] key) throws Exception 
	{

		MessageDigest digest = MessageDigest.getInstance("SHA-256");

		byte[] hash = digest.digest(key);

		StringBuilder result = new StringBuilder();

		for (int i = 0; i < 8; i++) 
		{
			result.append(String.format("%02x",hash[i] & 0xff));
		}

		return result.toString();
	}

	//Holds all keys belonging to one authenticated PQC session.
    public static class SessionKeys 
	{

        private final byte[] commandClientToServer;
        private final byte[] commandServerToClient;
        private final byte[] fileClientToServer;
        private final byte[] fileServerToClient;


        private SessionKeys(byte[] commandClientToServer,byte[] commandServerToClient,byte[] fileClientToServer,byte[] fileServerToClient) 
		{
            this.commandClientToServer = commandClientToServer;
            this.commandServerToClient = commandServerToClient;
            this.fileClientToServer = fileClientToServer;
            this.fileServerToClient = fileServerToClient;
        }


        public byte[] getCommandClientToServer() 
		{
            return commandClientToServer.clone();
        }


        public byte[] getCommandServerToClient() 
		{
            return commandServerToClient.clone();
        }


        public byte[] getFileClientToServer() 
		{
            return fileClientToServer.clone();
        }


        public byte[] getFileServerToClient() 
		{
            return fileServerToClient.clone();
        }
    }
}