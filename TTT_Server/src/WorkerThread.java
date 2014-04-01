

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.lang.Math;

public class WorkerThread extends Thread {

	private DatagramPacket rxPacket;
	private DatagramSocket socket;

	public WorkerThread(DatagramPacket packet, DatagramSocket socket) {
		this.rxPacket = packet;
		this.socket = socket;
	}

	@Override
	public void run() {
		// convert the rxPacket's payload to a string
		String payload = new String(rxPacket.getData(), 0, rxPacket.getLength())
				.trim();
		System.out.print("RECEIVED: " + payload + "\n");

		// dispatch request handler functions based on the payload's prefix

		if (payload.startsWith("REGISTER")) {
			onRegisterRequested(payload);
			return;
		}

		if (payload.startsWith("PLAY")) {
			onPlayRequested(payload);
			return;
		}

		if (payload.startsWith("UPDATE")) {
			onUpdateRequested(payload);
			return;
		}

		if (payload.startsWith("MOVE")) {
			onMoveRequested(payload);
			return;
		}
		
		if (payload.startsWith("END")) {
			onEndRequested(payload);
			return;
		}

		if (payload.startsWith("SHUTDOWN")) {
			onShutdownRequested(payload);
			return;
		}


		// if we got here, it must have been a bad request, so we tell the
		// client about it
		onBadRequest(payload);
	}

	// send a string, wrapped in a UDP packet, to the specified remote endpoint
	public void send(String payload, InetAddress address, int port)
			throws IOException {
		DatagramPacket txPacket = new DatagramPacket(payload.getBytes(),
				payload.length(), address, port);
		this.socket.send(txPacket);
	}

	/**
	 * REGISTER
	 * format:
	 * REGISTER
	 * returns the user's new ID.
	 * 
	 * @param payload
	 */
	private void onRegisterRequested(String payload) {

		int number;
		synchronized (Server.nextId) {
			number = Server.nextId++;
		}

		try {
			send("" + number + "\n", this.rxPacket.getAddress(),
					this.rxPacket.getPort());
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * PLAY
	 * format:
	 * PLAY userid
	 * if no one is waiting, puts userid in the waiting position.
	 * if someone is waiting, starts a game between userid and the waiter.
	 * the second person to join the game plays first (implemented in client)
	 * @param payload
	 */
	private void onPlayRequested(String payload) {
		int userid;
		try {
			Scanner line = new Scanner(payload);
			line.next();
			userid = line.nextInt();
		} catch (NoSuchElementException e) {
			onBadRequest(payload);
			return;
		}
		synchronized (Server.waiter) {
			if (Server.waiter == -1) {
				Server.waiter = userid;
				try {
					send("You are now waiting for an opponent\n",
							this.rxPacket.getAddress(), this.rxPacket.getPort());
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else if (userid == Server.waiter) {
				try {
					send("You are still waiting for an opponent\n",
							this.rxPacket.getAddress(), this.rxPacket.getPort());
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else {
				Server.opponent.put(userid, Server.waiter);
				Server.opponent.put(Server.waiter, userid);
				String key = makeKey(Server.waiter, userid);
				Server.gameboard.put(key, "---------");
				try {
					send("Game starting... \n", this.rxPacket.getAddress(),
							this.rxPacket.getPort());
				} catch (IOException e) {
					e.printStackTrace();
				}
				Server.waiter = -1;
			}
		}
	}

	/**
	 * UPDATE
	 * format:
	 * UPDATE userid
	 * sends the board for the game userid is in
	 * @param payload
	 */
	private void onUpdateRequested(String payload) {
		int userid;
		try {
			Scanner line = new Scanner(payload);
			line.next();
			userid = line.nextInt();
		} catch (NoSuchElementException e) {
			onBadRequest(payload);
			return;
		}

		if (Server.opponent.containsKey(userid)) {
			int opp = Server.opponent.get(userid);
			String key = makeKey(userid, opp);
			String board = Server.gameboard.get(key);
			try {
				send("" + board+ "\n", this.rxPacket.getAddress(),
						this.rxPacket.getPort());
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			try {
				send("No game started\n", this.rxPacket.getAddress(),
						this.rxPacket.getPort());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * MOVE
	 * format:
	 * MOVE userid index letter
	 * places 'letter' at index on the board (0-8) for the game userid is in
	 * @param payload
	 */
	private void onMoveRequested(String payload) {
		int userid;
		int index;
		String letter;
		char c;
		try {
			Scanner line = new Scanner(payload);
			line.next();
			userid = line.nextInt();
			index = line.nextInt();
			letter = line.next();
			c = letter.charAt(0);
		} catch (NoSuchElementException e) {
			onBadRequest(payload);
			return;
		}
		if (index < 0 || index > 8){
			try {
				send("index out of range\n", this.rxPacket.getAddress(),
						this.rxPacket.getPort());
				return;
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
		}
		if (Server.opponent.containsKey(userid)) {
			int opp = Server.opponent.get(userid);
			String key = makeKey(userid, opp);
			String board = Server.gameboard.get(key);
			board = board.substring(0, index) + c + board.substring(index+1, 9);
			Server.gameboard.put(key, board);
			try {
				send("" + board+ "\n", this.rxPacket.getAddress(),
						this.rxPacket.getPort());
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			try {
				send("You are not in a game\n", this.rxPacket.getAddress(),
						this.rxPacket.getPort());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
	}
	
	/**
	 * END
	 * format:
	 * END userid
	 * removes key-value pair from opponenets map, and from gameboard (unless the other user already deleted
	 * the gameboard)
	 * @param payload
	 */
	private void onEndRequested(String payload) {
		int userid;
		try {
			Scanner line = new Scanner(payload);
			line.next();
			userid = line.nextInt();
		} catch (NoSuchElementException e) {
			onBadRequest(payload);
			return;
		}
		int opp = Server.opponent.get(userid);
		Server.opponent.remove(userid);
		String key = makeKey(opp, userid);
		Server.gameboard.remove(key);
		
		
	}

	/**
	 * SHUTDOWN format: SHUTDOWN closes the socket if the request comes from
	 * localhost. First waits for all other threads to finish.
	 * 
	 * @param payload
	 */
	private void onShutdownRequested(String payload) {
		// the string is the address that I found packets sent via netcat to be
		// coming from.
		if (this.rxPacket.getAddress().toString().equals("/0:0:0:0:0:0:0:1"))
			;
		{
			for (WorkerThread t : Server.threads) {
				try {
					if (t != Thread.currentThread())
						t.join();
				} catch (InterruptedException e) {
				}
			}
			socket.close();
		}
	}

	private void onBadRequest(String payload) {
		try {
			send("BAD REQUEST\n", this.rxPacket.getAddress(),
					this.rxPacket.getPort());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private String makeKey(int a, int b) {
		int high = Math.max(a, b);
		int low = Math.min(a, b);
		return "" + high + "_" +  low;
	}

}
