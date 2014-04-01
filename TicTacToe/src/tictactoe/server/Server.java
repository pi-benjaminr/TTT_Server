package tictactoe.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;



public class Server {
	
	// constants
	public static final int DEFAULT_PORT = 20000;
	public static final int MAX_PACKET_SIZE = 512;
	
	// port number to listen on
	protected int port;
	
	
	protected static Integer nextId = 10000;
	
	//maps each player to their opponenet
	protected static final Map<Integer, Integer> opponent = Collections
			.synchronizedMap(new HashMap<Integer, Integer>());
	
	//maps a pair of players to their gameboard, represented as a string of nine characters.
	//the key is a concatenation of the userids
	protected static final Map<String, String> gameboard = Collections
			.synchronizedMap(new HashMap<String, String>());
	
	//waiter: a player currently waiting for an opponent.
	protected static Integer waiter = -1;
	
	//used for shutdown
	protected static final ArrayList<WorkerThread> threads = new ArrayList<WorkerThread>();
	
	Server(int port) {
		this.port = port;
	}
	
	public void start() {
		DatagramSocket socket = null;
		try {
			// create a datagram socket, bind to port port. See
			// http://docs.oracle.com/javase/tutorial/networking/datagrams/ for
			// details.

			socket = new DatagramSocket(port);

			// receive packets in an infinite loop
			while (!socket.isClosed()) {
				// create an empty UDP packet
				byte[] buf = new byte[Server.MAX_PACKET_SIZE];
				DatagramPacket packet = new DatagramPacket(buf, buf.length);
				// call receive (this will poulate the packet with the received
				// data, and the other endpoint's info)
				socket.receive(packet);
				// start up a worker thread to process the packet (and pass it
				// the socket, too, in case the
				// worker thread wants to respond)
				WorkerThread t = new WorkerThread(packet, socket);
				threads.add(t);
				t.start();
			}
		} catch (IOException e) {
			// we jump out here if there's an error, or if the worker thread (or
			// someone else) closed the socket
			e.printStackTrace();
		} finally {
			if (socket != null && !socket.isClosed())
				socket.close();
		}
	}
	
	
	
	// main method
	public static void main(String[] args) {
		int port = Server.DEFAULT_PORT;

		// check if port was given as a command line argument
		if (args.length > 0) {
			try {
				port = Integer.parseInt(args[0]);
			} catch (Exception e) {
				System.out.println("Invalid port specified: " + args[0]);
				System.out.println("Using default port " + port);
			}
		}

		// instantiate the server
		Server server = new Server(port);

		System.out
				.println("Starting server. Connect with netcat (nc -u localhost "
						+ port
						+ ") or start multiple instances of the client app to test the server's functionality.");

		// start it
		server.start();

		// ...when the socket has been closed and the start() function
		// completed...
		System.out.println("Server shutdown complete");

	}

}
