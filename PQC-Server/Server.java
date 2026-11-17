/*
	Names: Pedro Malavet
	Description: This is the main Server Program
*/

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.*;
import java.net.*;
import java.time.*;

public class Server
{

	public Server()
	{

		ServerSocket ClientListener;
		ServerSocket FileListener;
		Socket clientSocket;
		Socket fileSocket;
		ArrayList<ClientHandler> clients = new ArrayList<>();
		ExecutorService pool = Executors.newFixedThreadPool(8);

		try
		{
			Scanner input = new Scanner(System.in);

			//Get port number
			System.out.println("[Server] Please enter port number Client Requests");
			System.out.print(">");
			int port = input.nextInt();
			System.out.println("[Server] Please enter port number File Transfers");
			System.out.print(">");
			int port2 = input.nextInt();
			ClientListener = new ServerSocket(port);
			FileListener = new ServerSocket(port2);

			//Listen For requests
			while(true)
			{
				clientSocket = ClientListener.accept();
				fileSocket = FileListener.accept();
				System.out.println("[Server] Client Connected");
				ClientHandler clientThread = new ClientHandler(clientSocket,fileSocket);
				clients.add(clientThread);

				pool.execute(clientThread);
			}

		}
		catch(IOException e)
		{
			e.printStackTrace();
		}

	}


	//Call Constructor
	public static void main(String [] args)   
	{
		new Server();    
	}

}
