/*
	Names: Pedro Malavet
	Description: This program is send requests to the server
*/

import java.util.Scanner;
import java.util.ArrayList;
import java.util.List;
import java.io.*;
import java.net.*;
import java.time.*;
import java.nio.file.Paths;


public class Client
{

	public Client()
	{
		try
		{

			//Default Directory [Make sure you make a files directory before running] or just delete the /files from this line
			String currentDir = Paths.get("").toAbsolutePath().toString() + "/files";

			//Get Ip address and port num
			String ipAddress = getIpAddress();
			int port = getPortNumReq();
			int port2 = getPortNumFiles();

			//Establish Socket
			Socket s = new Socket(ipAddress,port);
			Socket s2 = new Socket(ipAddress,port2);
			
			
			// Create streams
			DataInputStream commandIn = new DataInputStream(s.getInputStream());
			DataOutputStream commandOut = new DataOutputStream(s.getOutputStream());
			DataInputStream fileIn = new DataInputStream(s2.getInputStream());
			DataOutputStream fileOut = new DataOutputStream(s2.getOutputStream());
			
			//Load Trusted public Key first
			byte[] serverPublicKey = java.nio.file.Files.readAllBytes(java.nio.file.Path.of("server_mldsa_public.key"));
						
			// ML-KEM handshake
			PQCHandshake.HandshakeResult handshake = PQCHandshake.clientHandshake(commandIn,commandOut,serverPublicKey);
			byte[] sharedSecret = handshake.getSharedSecret();
			byte[] transcriptHash = handshake.getTranscriptHash();
			
			//For Benchmarking
			String sessionId = BenchmarkLogger.createSessionId(transcriptHash); 
			BenchmarkLogger.log(sessionId,"CLIENT","ML-DSA-65 Verification",handshake.getMldsaNanos(),0);
			BenchmarkLogger.log(sessionId,"CLIENT","ML-KEM-768 Encapsulation",handshake.getMlkemEncapsulationNanos(),0);
			BenchmarkLogger.log(sessionId,"CLIENT","Total PQC Handshake",handshake.getTotalHandshakeNanos(),0);
			
			System.out.println("[Client] ML-KEM shared secret established.");
			
			//Record Start Time
			long hkdfStart = System.nanoTime();
			
			// Derive independent AES keys
			HKDFKeyDerivation.SessionKeys keys = HKDFKeyDerivation.deriveSessionKeys(sharedSecret,transcriptHash);
			
			//Record End Time
			long hkdfNanos = System.nanoTime() - hkdfStart;
			BenchmarkLogger.log(sessionId,"CLIENT","HKDF-SHA-256",hkdfNanos,0); //For Benchmarking
			
			java.util.Arrays.fill(sharedSecret,(byte) 0);
			//System.out.println("[Client] Transcript hash: " + shortHex(transcriptHash)); //For DB Transcript hash only
			java.util.Arrays.fill(transcriptHash,(byte) 0);
			
			//For Debugging HKDF only
			/*
			System.out.println("[Client] Command C->S: " + HKDFKeyDerivation.fingerprint(keys.getCommandClientToServer()));
			System.out.println("[Client] Command S->C: " + HKDFKeyDerivation.fingerprint(keys.getCommandServerToClient()));
			System.out.println("[Client] File C->S: " + HKDFKeyDerivation.fingerprint(keys.getFileClientToServer()));
			System.out.println("[Client] File S->C: " + HKDFKeyDerivation.fingerprint(keys.getFileServerToClient()));
			*/
			
			// AES-256-GCM channels
			SecureChannel commandChannel = new SecureChannel(keys.getCommandClientToServer(),keys.getCommandServerToClient());
			SecureChannel fileChannel = new SecureChannel(keys.getFileClientToServer(),keys.getFileServerToClient());
			System.out.println("[Client] HKDF session keys derived.");
			System.out.println("[Client] Authenticated PQC connection established.");
			
			
			
			showPrompt();

			while(true)
			{
				//Ask user for request
				String request = getRequest();
				

				if(!request.toUpperCase().equals("QUIT"))
				{

					String [] args = new String[0];
					//Check to see if cmd contains quotes
					if(request.contains("\""))
					{
						if(hasEvenQuotes(request))
						{
							args = extractQuotedStrings(request);
							args[0] = args[0].toUpperCase(); //Make 1st arg case insenitive
						}
					}
					else
					{
						args = request.split(" ");
						args[0] = args[0].toUpperCase(); //Make 1st arg case insenitive
					}

					
					//Send request to Server
					commandChannel.sendEncryptedString(commandOut,request); //<--


					//Send file to server
					if(args[0].equals("PUT"))
					{

						if(args[1].contains("\""))
						{
							args[1] = args[1].replaceAll("^\"|\"$", "");

							if(args[1].startsWith("/"))
							{
								sendFile(fileOut,args[1],fileChannel,sessionId);
							}
							else
							{
								sendFile(fileOut,currentDir + "/" + args[1],fileChannel,sessionId);
							}

						}
						else if(args[1].startsWith("/"))
						{
							sendFile(fileOut,args[1],fileChannel,sessionId);
						}
						else
						{
							sendFile(fileOut,currentDir + "/" + args[1],fileChannel,sessionId);
						}

					}

					//mark down start time
					LocalTime clientstarttime = LocalTime.now();
					LocalTime clientendtime;
					Duration clientresponsetime; 


					//Get file from server
					if(args[0].equals("GET"))
					{
						if(args[2].contains("\""))
						{
							args[2] = args[2].replaceAll("^\"|\"$", "");
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

					}

					//Get info from Server
					String str = "";
					while(!str.equals("stop"))
					{
					
						str = commandChannel.receiveEncryptedString(commandIn); //<--

						if(str.equals("stop"))
						{
							clientendtime = LocalTime.now();
							System.out.println("[Client] STOP Request for " + args[0] + "received");
							//System.out.println("[Client] Start Time:" + clientstarttime);
							//clientresponsetime = Duration.between(clientstarttime, clientendtime);
							//System.out.println("[Client] End Time:"+ clientendtime);
							//System.out.println("[Client] Response Time:" + clientresponsetime);
							break;
						}
						else
							System.out.println(str);
					}
					
				}
				else
					break;
			}

		}
		catch(Exception e)
		{
			e.printStackTrace();
		}

	}

	//GETs file from server
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


			System.out.println("[Client] Size of file to be received: " + sizeOfFile + " Bytes");


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
			BenchmarkLogger.log(sessionId,"CLIENT","GET Encrypted File Receive",transferNanos,bytesWritten);


			System.out.println("[Client] Encrypted file received successfully");
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	//PUTs file in server
	public synchronized void sendFile(DataOutputStream output,String path,SecureChannel secureChannel,String sessionId)
	{
		try
		{
			File file = new File(path);
			long sizeOfFile = file.length();
			
			//Record Start Time
			long transferStart = System.nanoTime();

			System.out.println("[Client] Size of file to be sent: " + sizeOfFile + " Bytes");


			
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
			BenchmarkLogger.log(sessionId,"CLIENT","PUT Encrypted File Send",transferNanos,sizeOfFile);

			System.out.println("[Client] Encrypted file sent");
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	//Requests User For IP address
	public String getIpAddress()
	{
		System.out.println("[Client] What is the IP Address of the Server");
		System.out.print(">");
		Scanner input = new Scanner(System.in);
		String ip = input.nextLine();
		return ip;
	}

	//Requests User for port number for Requests
	public int getPortNumReq()
	{
		System.out.println("[Client] Please enter port number Client Requests");
		System.out.print(">");
		Scanner input = new Scanner(System.in);
		int port = input.nextInt();
		return port;
	}

	//Requests User for port number for Files
	public int getPortNumFiles()
	{
		System.out.println("[Client] Please enter port number File Transfers");
		System.out.print(">");
		Scanner input = new Scanner(System.in);
		int port = input.nextInt();
		return port;
	}

	//Shows Prompt with options
	public void showPrompt()
	{
		System.out.println("[Client] Type in command followed by parameters");
		System.out.println("\t GET [Server Path] [Client Path]");
		System.out.println("\t PUT [Client Path] [Server Path]");
		System.out.println("\t CD [Server Path]");
		System.out.println("\t LS");
		System.out.println("\t PWD");
		System.out.println("\t QUIT");
	}

	//Checks if a sting has an even number 
	public static boolean hasEvenQuotes(String str) 
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
	public static String[] extractQuotedStrings(String str) 
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

	//Get string request
	public String getRequest()
	{
		Scanner input = new Scanner(System.in);
		boolean isVaild = false;
		String request= "";
		String [] args = new String[0];
		boolean evenOrNoQuotes = false;

		do
		{

			System.out.print(">");

			request = input.nextLine();

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


			//Vaildate Input
			if(args[0].equals("GET")  && evenOrNoQuotes) //GET
			{
				if(args.length == 3)
					isVaild = true;
				else if(args.length < 3)
				{
					System.out.println("[Client] Not enought arguments");
					isVaild = false;
				}
				else
				{
					System.out.println("[Client] Too many arguments");
					isVaild = false;
				}
			}
			else if(args[0].equals("PUT")  && evenOrNoQuotes) //PUT
			{
				if(args.length == 3)
					isVaild = true;
				else if(args.length < 3)
				{
					System.out.println("[Client] Not enought arguments");
					isVaild = false;
				}
				else
				{
					System.out.println("[Client] Too many arguments");
					isVaild = false;
				}
			}
			else if(args[0].equals("CD") && evenOrNoQuotes) //CD
			{

				if(args.length == 2)
				{
					isVaild = true;
				}
				else if(args.length < 2)
				{
					System.out.println("[Client] Not enought arguments");
					isVaild = false;
				}
				else
				{
					System.out.println("[Client] Too many arguments");
					isVaild = false;
				}
			}
			else if(args[0].equals("QUIT") && evenOrNoQuotes) //QUIT
			{
				if(args.length == 1)
					isVaild = true;
				else
				{
					System.out.println("[Client] Too many arguments");
					isVaild = false;
				}
			}
			else if(args[0].equals("LS") && evenOrNoQuotes) //LS
			{
				if(args.length <= 2)
					isVaild = true;
				else
				{
					System.out.println("[Client] Too many arguments");
					isVaild = false;
				}
			}
			else if(args[0].equals("PWD") && evenOrNoQuotes) //PWD
			{
				if(args.length == 1)
					isVaild = true;
				else
				{
					System.out.println("[Client] Too many arguments");
					isVaild = false;
				}
			}
			else
			{
				System.out.println("[Client] invaild command");
				isVaild = false;
			}

		}while(!isVaild);

		return request;

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

	//Call Constructor
	public static void main(String [] args)
	{
		new Client();
	}

}
