/*
	Names: Pedro Malavet
	Description: This is the class for the Server that handles the Requests coming from the client
*/

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Paths;

public class ClientHandler implements Runnable
{
	private Socket client;
	private Socket fileSocket;
	private DataInputStream commandIn;
	private DataOutputStream commandOut;
	private DataInputStream fileIn;
	private DataOutputStream fileOut;
	private SecureChannel commandChannel;
	private SecureChannel fileChannel;
   
   
   //Tells client to stop geting info
   public void SendStopRequest() throws Exception
   {
        commandChannel.sendEncryptedString(commandOut,"stop");
        System.out.println("[Server] Encrypted Stop Request sent");
   }
   
    //retrives file from client [PUT]
	public synchronized void retriveFile(DataInputStream input,String path,SecureChannel secureChannel,String sessionId)
	{
		try
		{
			//Record Start Time
			long transferStart = System.nanoTime();
			
			//Receive encrypted file-size packet.
			byte[] sizeBytes = secureChannel.receiveEncrypted(input);

			if (sizeBytes.length != Long.BYTES)
			{
				throw new IOException("Invalid encrypted file size packet");
			}

			long sizeOfFile = java.nio.ByteBuffer.wrap(sizeBytes).getLong();


			if (sizeOfFile < 0)
			{
				throw new IOException("Invalid file size: " + sizeOfFile);
			}


			System.out.println("[Server] Size of file to be received: " + sizeOfFile + " Bytes");


			File file = new File(path);

			long bytesWritten = 0;
			try (FileOutputStream fileOutput = new FileOutputStream(file))
			{
				

				while (bytesWritten < sizeOfFile)
				{
					
					 
					 
					//SecureChannel reads one complete AES-GCM packet and verifies its tag.
					byte[] plaintextChunk = secureChannel.receiveEncrypted(input);
					if (bytesWritten + plaintextChunk.length > sizeOfFile)
					{
						throw new IOException("Received more file data " + "than expected");
					}

					fileOutput.write(plaintextChunk);
					bytesWritten += plaintextChunk.length;
				}


				fileOutput.flush();
			}
			
			//Record End Time
			long transferNanos = System.nanoTime() - transferStart;
			BenchmarkLogger.log(sessionId,"SERVER","PUT Encrypted File Receive",transferNanos,bytesWritten);


			System.out.println("[Server] Encrypted file received successfully");
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	//sends file to client [GET]
	//PUTs file in server
	public synchronized void sendFile(DataOutputStream output,String path,SecureChannel secureChannel,String sessionId)
	{
		try
		{
			File file = new File(path);
			long sizeOfFile = file.length();
			
			//Record Start Time
			long transferStart = System.nanoTime();

			System.out.println("[Server] Size of file to be sent: " + sizeOfFile + " Bytes");


			
			//Encrypt the file size too.
			byte[] sizeBytes = java.nio.ByteBuffer.allocate(Long.BYTES).putLong(sizeOfFile).array();
			secureChannel.sendEncrypted(output,sizeBytes);


			try (FileInputStream fileInput = new FileInputStream(file))
			{
				byte[] buffer = new byte[64000];
				int bytesRead;

				while ((bytesRead = fileInput.read(buffer))!= -1)
				{
					
					//Only encrypt bytes that were actually read.
					byte[] chunk = java.util.Arrays.copyOf(buffer,bytesRead);

					secureChannel.sendEncrypted(output,chunk);
				}
			}
			
			//Record End Time
			long transferNanos =System.nanoTime() - transferStart;
			BenchmarkLogger.log(sessionId,"SERVER","GET Encrypted File Send",transferNanos,sizeOfFile);

			System.out.println("[Server] Encrypted file sent");
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
   
   //Constuctor For Class
   public ClientHandler(Socket clientSocket,Socket fileSocket) throws IOException
	{
		this.client = clientSocket;
		this.fileSocket = fileSocket;
		commandIn = new DataInputStream(client.getInputStream());
		commandOut = new DataOutputStream(client.getOutputStream());
		fileIn = new DataInputStream(fileSocket.getInputStream());
		fileOut = new DataOutputStream(fileSocket.getOutputStream());
	}
   
   //Sets Delay
   public void Delay()
   {
        long delay = 1000;
        System.out.println("[Server] Delaying for " + delay + "milli sec(s)");

        try
        {
			Thread.sleep(delay);
        }
        catch(InterruptedException e)
        {
			e.printStackTrace();
        }

   }
   
   //Check if a request contains even quotes
   public boolean hasEvenQuotes(String str) 
   {
        int quoteCount = 0;
        
        for (char ch : str.toCharArray()) 
		{
            if (ch == '"') 
			{
                quoteCount++;
            }
        }
        
        return quoteCount % 2 == 0; // Return true if even
	}
	
	//Spit the request for requests that contain quotes
	public String[] extractQuotedStrings(String str) 
	{
        List<String> quotesList = new ArrayList<>();
        StringBuilder currentQuote = new StringBuilder();
        boolean inQuotes = false;
        String [] orgStrAsArray = str.split(" ");

        for (char ch : str.toCharArray()) 
		{
            if (ch == '"') 
			{
                if (inQuotes) 
				{
                    quotesList.add(currentQuote.toString());
                    currentQuote.setLength(0); // Reset for the next quote
                }
                inQuotes = !inQuotes; // Toggle the inQuotes state
            } 
			else if (inQuotes) 
			{
                currentQuote.append(ch);
            }
        }

        String [] newArray = new String[quotesList.size() + 1];

        newArray[0] = orgStrAsArray[0];

        for(int i=1;i < newArray.length;i++)
        {
			newArray[i] = "\"" + quotesList.get(i -1) + "\"";
        }

        return newArray;
    }
	
	//Get the directory before the default directory
	public String getDirBefore(String currentDir)
	{
		String [] currentDirSplit = currentDir.split("/");
		String prevDir = "";
		
		for(int i = 0;i < currentDirSplit.length -1; i++)
		{
			if(i != 0)
				prevDir += "/";
			prevDir += currentDirSplit[i];
			
		}
		
		return prevDir;
	}
	
	//For DB Transcript Hashes only
	private static String shortHex(byte[] data)
	{

		StringBuilder result = new StringBuilder();

		for(int i = 0;i < Math.min(8, data.length);i++) 
		{
			result.append(String.format("%02x",data[i] & 0xff));
		}

		return result.toString();
	}
	
   @Override
   public void run()
   {
	  
	  
	  //Default Directory [Make sure you make a files directory before running] or just delete the /files from this line
	  String currentDir = Paths.get("").toAbsolutePath().toString() + "/files";   
	  
	  //Get Directory Before Default Directory
	  String prevDir = getDirBefore(currentDir); 
	  
	  String request = "";
	  String [] args = new String[0];
	  boolean evenOrNoQuotes = false;
	  
	  try
      {
         
		//Server Private Key
		byte[] serverSecretKey = java.nio.file.Files.readAllBytes(java.nio.file.Path.of("server_mldsa_secret.key"));
		 
		System.out.println("[Server] Starting PQC handshake...");
		
		
		PQCHandshake.HandshakeResult handshake = PQCHandshake.serverHandshake(commandIn,commandOut,serverSecretKey);
		byte[] sharedSecret = handshake.getSharedSecret();
		byte[] transcriptHash = handshake.getTranscriptHash();
		
		//For Benchmarking
		String sessionId = BenchmarkLogger.createSessionId(transcriptHash);
		BenchmarkLogger.log(sessionId,"SERVER","ML-DSA-65 Signing",handshake.getMldsaNanos(),0);
		BenchmarkLogger.log(sessionId,"SERVER","ML-KEM-768 Key Generation",handshake.getMlkemKeyGenerationNanos(),0);
		BenchmarkLogger.log(sessionId,"SERVER","ML-KEM-768 Decapsulation",handshake.getMlkemDecapsulationNanos(),0);
		BenchmarkLogger.log(sessionId,"SERVER","Total PQC Handshake",handshake.getTotalHandshakeNanos(),0);
		System.out.println("[Server] ML-KEM shared secret established.");
		
		//Record Start Time
		long hkdfStart = System.nanoTime();

		// Derive session keys
		HKDFKeyDerivation.SessionKeys keys = HKDFKeyDerivation.deriveSessionKeys(sharedSecret,transcriptHash);
		
		//Record End Time
		long hkdfNanos = System.nanoTime() - hkdfStart;
		BenchmarkLogger.log(sessionId,"SERVER","HKDF-SHA-256",hkdfNanos,0); //For Benchmarking
		
		java.util.Arrays.fill(sharedSecret,(byte) 0);
		//System.out.println("[Server] Transcript hash: " + shortHex(transcriptHash)); //For DB Transcript hash only
		java.util.Arrays.fill(transcriptHash,(byte) 0);
		
		//For Debugging HKDF only
		/*
		System.out.println("[Server] Command C->S: " + HKDFKeyDerivation.fingerprint(keys.getCommandClientToServer()));
		System.out.println("[Server] Command S->C: " + HKDFKeyDerivation.fingerprint(keys.getCommandServerToClient()));
		System.out.println("[Server] File C->S: " + HKDFKeyDerivation.fingerprint(keys.getFileClientToServer()));
		System.out.println("[Server] File S->C: " + HKDFKeyDerivation.fingerprint(keys.getFileServerToClient()));
		*/
		
		commandChannel = new SecureChannel(keys.getCommandServerToClient(),keys.getCommandClientToServer());
		fileChannel = new SecureChannel(keys.getFileServerToClient(),keys.getFileClientToServer());
		System.out.println("[Server] HKDF session keys derived.");
		System.out.println("[Server] Secure PQC connection established.");
		
		
		 
         while(true)
         {
            request = commandChannel.receiveEncryptedString(commandIn); //request from client
			
			//Check to see if cmd contains quotes
			if(request.contains("\""))
			{
				if(hasEvenQuotes(request))
				{
					args = extractQuotedStrings(request);
					args[0] = args[0].toUpperCase(); //Make 1st arg case insenitive
					evenOrNoQuotes = true;
				}
			}
			else
			{
				args = request.split(" ");
				args[0] = args[0].toUpperCase(); //Make 1st arg case insenitive
				evenOrNoQuotes = true;
			}
			
			
            if(args[0].equals("GET") && evenOrNoQuotes) //GET
            {                     

				if(args[1].contains("\""))
				{
					args[1] = args[1].replaceAll("^\"|\"$", ""); //Strip quotes
					
					if(args[1].startsWith("/"))
					{
						sendFile(fileOut,args[1], fileChannel,sessionId);
					}
					else
					{
						sendFile(fileOut,currentDir + "/" + args[1], fileChannel,sessionId);
					}
				}
				else if(args[1].startsWith("/"))
				{
					sendFile(fileOut,args[1], fileChannel,sessionId);
				}
				else
				{
					sendFile(fileOut,currentDir + "/" + args[1], fileChannel,sessionId);
				}
				
				System.out.println("[Server] Sent GET to client");
				//Delay();
				SendStopRequest();
				
				
            }
            else  if(args[0].equals("PUT") && evenOrNoQuotes) //PUT
            {
				
				if(args[2].contains("\""))
				{
					args[2] = args[2].replaceAll("^\"|\"$", ""); //Strip quotes
					
					if(args[2].startsWith("/"))
					{
						retriveFile(fileIn,args[2],fileChannel,sessionId);
					}
					else
					{
						retriveFile(fileIn,currentDir + "/" + args[2],fileChannel,sessionId);
					}
					
				}
				else if(args[2].startsWith("/"))
				{
					
					retriveFile(fileIn,args[2],fileChannel,sessionId);
				}
				else
				{
					retriveFile(fileIn,currentDir + "/" + args[2],fileChannel,sessionId);
				}
				
				System.out.println("[Server] File received");
				//Delay();
				SendStopRequest();
				

            }
            else if(args[0].equals("CD") && evenOrNoQuotes) //CD
            {
				System.out.println("[Server] Sent CD to client");
				prevDir = currentDir;
				
				if(args[1].contains("\""))
				{
					args[1] = args[1].replaceAll("^\"|\"$", ""); //strip quotes
					
					if(args[1].startsWith("/"))
					{
						File directory = new File(args[1]);
						if (directory.exists() && directory.isDirectory())
						{
							prevDir = currentDir;
							currentDir = args[1];
							commandChannel.sendEncryptedString(commandOut,"[Server] Directory Changed to " + currentDir);
						}
						else
							commandChannel.sendEncryptedString(commandOut,"[Server] Directory " + args[1] + " does not exist");
					}
					else
					{
						
						File directory = new File(currentDir + "/" + args[1]);
						if (directory.exists() && directory.isDirectory())
						{
							prevDir = currentDir;
							currentDir = currentDir + "/" + args[1];
							commandChannel.sendEncryptedString(commandOut,"[Server] Directory Changed to " + currentDir);
						}
						else
							commandChannel.sendEncryptedString(commandOut,"[Server] Directory " + currentDir + "/" + args[1] + " does not exist");
					}
					
				}
				else if(args[1].equals(".."))
				{
					currentDir = getDirBefore(currentDir);
					prevDir = getDirBefore(currentDir);
					commandChannel.sendEncryptedString(commandOut,"[Server] Directory Changed to " + currentDir);
				}
				else if(args[1].startsWith("/"))
				{
					File directory = new File(args[1]);
					if (directory.exists() && directory.isDirectory())
					{
						prevDir = currentDir;
						currentDir = args[1];
						commandChannel.sendEncryptedString(commandOut,"[Server] Directory Changed to " + currentDir);
					}
					else
						commandChannel.sendEncryptedString(commandOut,"[Server] Directory " + args[1] + " does not exist");
				}
				else
				{
					
					File directory = new File(currentDir + "/" + args[1]);
					if (directory.exists() && directory.isDirectory())
					{
						prevDir = currentDir;
						currentDir = currentDir + "/" + args[1];
						commandChannel.sendEncryptedString(commandOut,"[Server] Directory Changed to " + currentDir);
					}
					else
						commandChannel.sendEncryptedString(commandOut,"[Server] Directory " + currentDir + "/" + args[1] + " does not exist");
				}
					
				//Delay();
				SendStopRequest();
            }
			else if(args[0].equals("LS") && evenOrNoQuotes) //LS
			{
				System.out.println("[Server] Sent LS to client");
				String filepath =  currentDir;
				File directory = new File(filepath);
				if (directory.exists() && directory.isDirectory()) 
				{
					String[] files = directory.list();
					for (String file : files) 
					{
						System.out.println(file);
						commandChannel.sendEncryptedString(commandOut,file);
						//Delay();
					}
				}
				
				//Delay();
				SendStopRequest();
			}
			else if(args[0].equals("PWD") && evenOrNoQuotes) //PWD
			{
				System.out.println("[Server] Sent PWD to client");
				System.out.println("[Server] PWD returns: "  + currentDir);
				commandChannel.sendEncryptedString(commandOut,"[Server] " + currentDir);
				//Delay();
				SendStopRequest();
			}
			else
			{
				System.out.println("[Server] Bad Request sent alerting client");
				commandChannel.sendEncryptedString(commandOut,"[Server] Bad Request");
				//Delay();
				SendStopRequest();
			}
				
         }
      
      }
      catch(Exception e)
      {
           e.printStackTrace();
      }
      
      
   }
}
