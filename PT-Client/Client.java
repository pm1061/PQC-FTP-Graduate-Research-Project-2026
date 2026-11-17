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
			//s.setTcpNoDelay(true);
			//s2.setTcpNoDelay(true);
			String sessionId = java.util.UUID.randomUUID().toString(); //Generate Sesion ID
			PrintWriter sessionWriter = new PrintWriter(s.getOutputStream(),true);
			sessionWriter.println("BENCHMARK_SESSION "+ sessionId);
			//System.out.println("[Client] Benchmark Session: "+ sessionId);
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
					PrintWriter pr = new PrintWriter(s.getOutputStream());
					pr.println(request);
					pr.flush();


					//Send file to server
					if(args[0].equals("PUT"))
					{
						String putPath;
						
						if(args[1].contains("\""))
						{
							args[1] = args[1].replaceAll("^\"|\"$", "");
						}
						
						if(args[1].startsWith("/"))
						{
							putPath = args[1];
						}
						else
						{
							putPath = currentDir + "/" + args[1];
						}
						
						File putFile = new File(putPath);
						long fileSize = putFile.length();
						long transferStart = System.nanoTime();
						sendFile(s2.getOutputStream(),putPath);
						long transferNanos = System.nanoTime() - transferStart;
						PlaintextBenchmarkLogger.log(sessionId,"CLIENT","PUT Plaintext File Send",transferNanos,fileSize);

					}


					//Get file from server
					if(args[0].equals("GET"))
					{
						String getPath;
						
						if(args[2].contains("\""))
						{
							args[2] = args[2].replaceAll("^\"|\"$","");
						}
						
						if(args[2].startsWith("/"))
						{
							getPath = args[2];
						}
						else
						{
							getPath = currentDir + "/" + args[2];
						}
						
						long transferStart = System.nanoTime();
						long bytesReceived = retriveFile(s2.getInputStream(),getPath);
						long transferNanos = System.nanoTime() - transferStart;
						
						if (bytesReceived >= 0) 
						{
							PlaintextBenchmarkLogger.log(sessionId,"CLIENT","GET Plaintext File Receive",transferNanos,bytesReceived);
						}

					}

					//Get info from Server
					String str = "";
					while(!str.equals("stop"))
					{
					
						InputStreamReader in = new InputStreamReader(s.getInputStream());
						BufferedReader bf = new BufferedReader(in);
						str = bf.readLine();

						if(str.equals("stop"))
						{
							System.out.println("[Client] STOP Request for " + args[0] + "received");
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
		catch(IOException e)
		{
			e.printStackTrace();
		}

	}

	//GETs file from server
	public synchronized long retriveFile(InputStream fin,String path)
	{
		try
		{
			DataInputStream din = new DataInputStream(fin);
			
			File file = new File(path);
			long sizeOfFile = din.readLong(); //Get size of file before writing
			
			System.out.println("[Client] Size of file to be received: " + sizeOfFile + " Bytes");
			FileOutputStream fileOut = new FileOutputStream(file);
			byte[] buffer = new byte[64000];
			int bytesRead;
			long bytesWriten = 0;

			while((bytesRead = fin.read(buffer)) != -1) 
			{

				bytesWriten += bytesRead;
				fileOut.write(buffer, 0, bytesRead);
				//fileOut.flush();
				
				if(bytesWriten >= sizeOfFile)
					break;
			}
			fileOut.flush();
			fileOut.close();
			
			return bytesWriten;
		}
		catch(IOException e)
		{
			e.printStackTrace();
			return -1;
		}

	}

	//PUTs file in server
	public synchronized void sendFile(OutputStream output,String path)
	{
		try
		{
			DataOutputStream dos = new DataOutputStream(output);
			
			File file = new File(path);
			long sizeOfFile = file.length();
			dos.writeLong(sizeOfFile); //Tell server size of file
			
			System.out.println("[Client] Size of file to be sent: " + sizeOfFile + " Bytes");
			FileInputStream fileInput = new FileInputStream(file);
			byte[] buffer = new byte[64000];
			int bytesRead;

			while ((bytesRead = fileInput.read(buffer)) != -1) 
			{
				
				output.write(buffer, 0, bytesRead);
				if (bytesRead == -1) 
				{
					break;
				}
			}
			
			fileInput.close();
			System.out.println("[Client] File sent");
		}
		catch(IOException e)
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

	//Call Constructor
	public static void main(String [] args)
	{
		new Client();
	}

}
