/*
	Names: Pedro Malavet
	Description: This is the class for the PQC Authentication
*/

import org.openquantumsafe.Signature;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;

public class PQCAuthentication 
{

    public static final String ALGORITHM = "ML-DSA-65";
    private static final String PROTOCOL_LABEL = "PQC-FTP-SERVER-AUTH-V1";

    /*
     * Construct exactly the bytes that will
     * be signed/verified.
     *
     * We bind:
     *
     *   protocol identifier
     *   client nonce
     *   server ML-KEM public key
     *
     * together into one authenticated transcript.
     */
    public static byte[] createServerTranscript(byte[] clientNonce,byte[] kemPublicKey) throws Exception 
	{

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);

        byte[] label = PROTOCOL_LABEL.getBytes(StandardCharsets.UTF_8);

        out.writeInt(label.length);
        out.write(label);

        out.writeInt(clientNonce.length);
        out.write(clientNonce);

        out.writeInt(kemPublicKey.length);
        out.write(kemPublicKey);

        out.flush();

        return buffer.toByteArray();
    }

	//Server signs the handshake transcript.
    public static byte[] signServerHandshake(byte[] secretKey,byte[] transcript) 
	{

        Signature signer = null;

        try 
		{
            signer = new Signature(ALGORITHM,secretKey);
            return signer.sign(transcript);
        }
        finally 
		{

            if (signer != null) 
			{
                signer.dispose_sig();
            }
        }
    }


    //Client authenticates the server.
    public static boolean verifyServerHandshake(byte[] publicKey,byte[] transcript,byte[] signature) 
	{

        Signature verifier = null;

        try 
		{

            verifier = new Signature(ALGORITHM);

            return verifier.verify(transcript,signature,publicKey);

        }
        finally 
		{

            if (verifier != null) 
			{
                verifier.dispose_sig();
            }
        }
    }
}