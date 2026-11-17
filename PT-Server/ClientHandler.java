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
   private BufferedReader in;
   private PrintWriter out;
   private FileOutputStream fileOut;
   private InputStream fin;
   private String sessionId;
   
   //Tells client to stop geting info
   public void SendStopRequest()
   {
        out.println("stop");
        System.out.println("[Server] Stop Request sent");
   }
   
    //retrives file from client [PUT]
	public synchronized long retriveFile(InputStream fin,String path)
	{
		try
		{
			DataInputStream din = new DataInputStream(fin);
			
			File file = new File(path);
			long sizeOfFile = din.readLong(); //Get size of file before writing
			
			System.out.println("[Server] Size of file to be received: " + sizeOfFile + " Bytes");
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
	
	//sends file to client [GET]
	public synchronized void sendFile(OutputStream output,String path)
	{
		try
		{
			DataOutputStream dos = new DataOutputStream(output);
			
			File file = new File(path);
			long sizeOfFile = file.length();
			dos.writeLong(sizeOfFile); //Tell client size of file
			
			System.out.println("[Server] Size of file to be sent: " + sizeOfFile + " Bytes");
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
			System.out.println("[Server] File sent");
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
		
	}
   
   //Constuctor For Class
   public ClientHandler(Socket clientSocket, Socket fileSocket) throws IOException
   {
      this.client = clientSocket;
      in = new BufferedReader(new InputStreamReader(client.getInputStream()));
      out = new PrintWriter(client.getOutputStream(),true);
	  
	  this.fileSocket = fileSocket;
	  fin = fileSocket.getInputStream();
	  
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
   
   @Override
   public void run()
   {
      
	  String currentDir = Paths.get("").toAbsolutePath().toString() + "/files";
	  String prevDir = getDirBefore(currentDir);
	  String request = "";
	  String [] args = new String[0];
	  boolean evenOrNoQuotes = false;
	  
	  try
      {
         
		String sessionMessage = in.readLine();
		 
		if (sessionMessage != null && sessionMessage.startsWith("BENCHMARK_SESSION ")) 
		{
			sessionId = sessionMessage.substring("BENCHMARK_SESSION ".length());
		}
		else 
		{
			throw new IOException("Missing benchmark session ID");
		}
		//System.out.println("[Server] Benchmark Session: "+ sessionId);

         while(true)
         {
            request = in.readLine(); //request from client
			
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
			
			//For DeBugging
			//for(int i=0;i < args.length;i++)
			//	System.out.println("[Server] Arg(" + i + "): " + args[i]);
			//System.out.println("[Server] Length of args:" + args.length);
			
            if(args[0].equals("GET") && evenOrNoQuotes) //GET
            {                     

				String getPath;

				if(args[1].contains("\""))
				{
					args[1] = args[1].replaceAll("^\"|\"$", "");
				}
				
				if(args[1].startsWith("/"))
				{
					getPath = args[1];
				}
				else
				{
					getPath =  currentDir + "/" + args[1];
				}
				
				File getFile = new File(getPath);
				long fileSize = getFile.length();
				long transferStart = System.nanoTime();
				sendFile(fileSocket.getOutputStream(),getPath);
				long transferNanos = System.nanoTime() - transferStart;

				PlaintextBenchmarkLogger.log(sessionId,"SERVER","GET Plaintext File Send",transferNanos,fileSize);
				
				
				System.out.println("[Server] Sent GET to client");
				//Delay();
				SendStopRequest();
				
				
            }
            else  if(args[0].equals("PUT") && evenOrNoQuotes) //PUT
            {
				
				String putPath;
				
				if(args[2].contains("\""))
				{
					args[2] = args[2].replaceAll("^\"|\"$", ""); //Strip quotes
				}
				
				if(args[2].startsWith("/"))
				{
					putPath = args[2];
				}
				else
				{
					putPath = currentDir + "/"+ args[2];
				}
				
				long transferStart = System.nanoTime();
				long bytesReceived = retriveFile(fin,putPath);
				long transferNanos = System.nanoTime() - transferStart;
				
				if (bytesReceived >= 0) 
				{
					PlaintextBenchmarkLogger.log(sessionId,"SERVER","PUT Plaintext File Receive",transferNanos,bytesReceived);
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
							out.println("[Server] Directory Changed to " + currentDir);
						}
						else
							out.println("[Server] Directory " + args[1] + " does not exist");
					}
					else
					{
						
						File directory = new File(currentDir + "/" + args[1]);
						if (directory.exists() && directory.isDirectory())
						{
							prevDir = currentDir;
							currentDir = currentDir + "/" + args[1];
							out.println("[Server] Directory Changed to " + currentDir);
						}
						else
							out.println("[Server] Directory " + currentDir + "/" + args[1] + " does not exist");
					}
					
				}
				else if(args[1].equals(".."))
				{
					currentDir = getDirBefore(currentDir);
					prevDir = getDirBefore(currentDir);
					out.println("[Server] Directory Changed to " + currentDir);
				}
				else if(args[1].startsWith("/"))
				{
					File directory = new File(args[1]);
					if (directory.exists() && directory.isDirectory())
					{
						prevDir = currentDir;
						currentDir = args[1];
						out.println("[Server] Directory Changed to " + currentDir);
					}
					else
						out.println("[Server] Directory " + args[1] + " does not exist");
				}
				else
				{
					
					File directory = new File(currentDir + "/" + args[1]);
					if (directory.exists() && directory.isDirectory())
					{
						prevDir = currentDir;
						currentDir = currentDir + "/" + args[1];
						out.println("[Server] Directory Changed to " + currentDir);
					}
					else
						out.println("[Server] Directory " + currentDir + "/" + args[1] + " does not exist");
				}
					
				//Delay();
				SendStopRequest();
            }
			else if(args[0].equals("LS") && evenOrNoQuotes) //LS
			{
				System.out.println("[Server] Sent LS to client");
				String filepath =  currentDir;
				File directory = new File(filepath);
				String fileList = "";
				
				if (directory.exists() && directory.isDirectory()) 
				{
					String[] files = directory.list();
					for (String file : files) 
					{
						fileList += file + " ";
						
						//Delay();
					}
					out.println(fileList);
				}
				
				//Delay();
				SendStopRequest();
			}
			else if(args[0].equals("PWD") && evenOrNoQuotes) //PWD
			{
				System.out.println("[Server] Sent PWD to client");
				System.out.println("[Server] PWD returns: "  + currentDir);
				out.println("[Server] " + currentDir);
				//Delay();
				SendStopRequest();
			}
			else
			{
				System.out.println("[Server] Bad Request sent alerting client");
				out.println("[Server] Bad Request");
				//Delay();
				SendStopRequest();
			}
				
         }
      
      }
      catch(IOException e)
      {
           e.printStackTrace();
      }
      finally
      {
            out.close();
            
            try
            {
               in.close();
            }
            catch(IOException e)
            {
               e.printStackTrace();
            }

      }
      
   }
}
