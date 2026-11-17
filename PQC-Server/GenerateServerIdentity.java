/*
	Names: Pedro Malavet
	Description: This class used to create the private and public keys for the server.
	Be sure to copy the public key to the Client machine.
*/

import org.openquantumsafe.Signature;

import java.nio.file.Files;
import java.nio.file.Path;

public class GenerateServerIdentity 
{

    private static final String ALGORITHM = "ML-DSA-65";

    public static void main(String[] args) 
	{

        Signature signer = null;

        try 
		{

            System.out.println("Generating " + ALGORITHM + " server identity...");

            signer = new Signature(ALGORITHM);

            byte[] publicKey = signer.generate_keypair();
            byte[] secretKey = signer.export_secret_key();


            Files.write(Path.of("server_mldsa_public.key"),publicKey);
            Files.write(Path.of("server_mldsa_secret.key"),secretKey);


            System.out.println("Public key: " + publicKey.length + " bytes");
            System.out.println("Secret key: " + secretKey.length + " bytes");
            System.out.println();
            System.out.println("Created:");

            System.out.println("server_mldsa_public.key");

            System.out.println("server_mldsa_secret.key");

        }
        catch (Exception e)
		{
            e.printStackTrace();
        }
        finally 
		{

            if (signer != null) 
			{
                signer.dispose_sig();
            }
        }
    }
}