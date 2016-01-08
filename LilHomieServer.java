import java.io.*;
import java.net.*;
import java.util.*;

//Lil Homie Transport Layer Protocol Server (unidirectional, stop-and-wait)
//Server passively listens for connections, and then receives data on these connections
//The only messages the server sends are control messages for ACKing, connecting, and closing
public class LilHomieServer {

	private DatagramSocket socket; // communication socket
	private InetAddress destIP; // what IP we end up connecting to
	private int destPort; // what port on that IP we end up connecting to
	private int srcPort; // what port we listen on
	private boolean connected; // are we connected
	private int secretNumber; // used for SYN cookie, randomly generated
	private int clientSeqNum; // what seq number are they using (what ACK number should I use)

	// empty packet is 29 bytes
	// this means an actual payload could be 995 bytes? 995 chars?
	private final int SEQ_NUM_LIMIT = 10000;
	private final int DATAGRAM_PAYLOAD_SIZE = 1024;

	// construct server object
	public LilHomieServer(int srcPort) {
		this.srcPort = srcPort;
		connected = false;
		secretNumber = -1;
	}
	
	// Output: True/False did we connect to someone
	public boolean lilHomieListen() {
		System.out.println("\n$ Heyo, homeslice\n$ Let's see if we can find someone to talk to...");
		try {
			socket = new DatagramSocket(srcPort); // try to open a socket
		} catch (SocketException e) {
			System.out.println("$ Unexpected program deviation, brother (1)");
			System.out.println("$ Error message: " + e.getMessage());
			System.out.println("$ Stopped listening for connections, dawg");
			socket = null;
			return false;
		}
//		try {
//			socket.setSoTimeout(timeout); // played around with timeouts in this method, but they didn't work how I wanted
//		} catch (SocketException e) {
//			System.out.println("$ Unexpected program deviation, brother (2)");
//			System.out.println("$ Error message: " + e.getMessage());
//			System.out.println("$ Stopped listening for connections, dawg");
//			socket = null;
//			return false;
//		}
		DatagramPacket rPacket = new DatagramPacket(new byte[DATAGRAM_PAYLOAD_SIZE], DATAGRAM_PAYLOAD_SIZE);
		//try {
			try {
				socket.receive(rPacket); // block here until we receive one; NO TIMEOUT
			} catch (IOException e1) {
				System.out.println("IOException, big guy");
				socket = null;
				return false;
			}
//		} catch (SocketTimeoutException e) {
//			System.out.println("$ Socket timed out. Ouch, no one wants to talk to you, dude");
//			socket = null;
//			return false;
//		} catch (Exception e) {
//			System.out.println("$ Unexpected program deviation, brother (3)");
//			System.out.println("$ Error message: " + e.getMessage());
//			System.out.println("$ Stopped listening for connections, dawg");
//			socket = null;
//			return false;
//		}
		//if you're here, someone has sent you a packet
		int expectedACK = 0;
		clientSeqNum = 0;
		while (!connected) { // until we are connected
			boolean SYNreceived = false;
			while (!SYNreceived) { // did we get a SYN
				destIP = rPacket.getAddress(); // get the IP of where this packet came from
				destPort = rPacket.getPort(); // get the port of where this packet came from
				String data = new String(rPacket.getData()); 
				String[] fields = data.split(",",-1); // split into fields
				if (fields[5].equals("1")) { // if its a SYN
					if (fields[4].equals("0")) { // and its not a RST
						System.out.println("$ Client at " + destIP + " on port " + destPort + " is initiating a connection, homie");
						System.out.println("$ Responding with SYN cookie, bro");
						clientSeqNum = Integer.valueOf(fields[2]); // get the seq num we need
						SYNreceived = true; //it's a SYN, move on to send a SYn of our own
					} else { // reset if we receive RST
						System.out.println("$ Client requested RST, mane (1)");
						System.out.println("$ Stopped listening for connections, dawg");
						socket = null;
						destIP = null;
						return false;
					}
				} else { // it's not a SYN, send a RST in case client had assumed a connection with us
					System.out.println("$ Client did not initiate with SYN as expected, man");
					System.out.println("$ Stopped listening for connections, dawg");
					DatagramPacket RST = packetize(rPacket.getAddress(), rPacket.getPort(), 0, 0, false, true, false, false, 0, null);
					try {
						socket.send(RST);
					} catch (IOException e) {
						System.out.println("$ Unexpected program deviation, brother (4)");
						System.out.println("$ Error message: " + e.getMessage());
						System.out.println("$ Connection failed, dawg");
						secretNumber = -1;
						socket = null;
						destIP = null;
						return false;
					}
					return false;
				}
			}
			boolean SYNACKreceived = false;
			while (!SYNACKreceived) { // send a SYN cookie and then wait to receive a SYN ACK
				Random rand = new Random();
				secretNumber = rand.nextInt(SEQ_NUM_LIMIT+1); // generate secret number for connection
				int secretHash = destIP.hashCode() ^ destPort ^ secretNumber; // hash with IP and port of other host
				DatagramPacket sPacket = packetize(destIP, destPort, clientSeqNum+1, secretHash, false, false, true, false, 0, null);
				try {
					socket.send(sPacket); // attempt to send, our secretHash is the seq number we sent
				} catch (IOException e) {
					System.out.println("$ Unexpected program deviation, brother (4)");
					System.out.println("$ Error message: " + e.getMessage());
					System.out.println("$ Connection failed, dawg");
					secretNumber = -1;
					socket = null;
					destIP = null;
					return false;
				}
				try {
					socket.setSoTimeout(1000); // timeout after 1 sec
				} catch (SocketException e) {
					System.out.println("$ Unexpected program deviation, brother (5)");
					System.out.println("$ Error message: " + e.getMessage());
					System.out.println("$ Connection failed, dawg");
					secretNumber = -1;
					socket = null;
					destIP = null;
					return false;
				}
				try {
					rPacket = new DatagramPacket(new byte[DATAGRAM_PAYLOAD_SIZE], DATAGRAM_PAYLOAD_SIZE);
					socket.receive(rPacket);
					destIP = rPacket.getAddress(); // if we get here, then we received a response, let's check it out
					destPort = rPacket.getPort();
					expectedACK = (destIP.hashCode() ^ destPort ^ secretNumber) + 1; // Reget IP and port from packet, hash again
					String data = new String(rPacket.getData());
					String[] fields = data.split(",",-1); // split into fields
					if (fields[1].equals(Integer.toString(expectedACK))) { // did the same host return the hash as an ACK? if so, then the client is not trying to hurt
						if (fields[5].equals("1")) { // is it a SYN
							if (fields[4].equals("0")) { // is it a RST?
								if (fields[3].equals("1")) { // is it an ACK?
									System.out.println("$ Client responded to SYN cookie with a SYN-ACK, baller");
									System.out.println("$ Client authenticated, mane");
									clientSeqNum = Integer.valueOf(fields[2]); // get seq number for transmissions after connection
									SYNACKreceived = true; // it is a SYN ACK, and the SYN cookie checked out, time to connect
								} else {

								}
							} else {
								System.out.println("$ Client requested RST, mane (2)");
								System.out.println("$ Connection failed, dawg");
								secretNumber = -1;
								socket = null;
								destIP = null;
								return false; // reset requested, restore state variables
							}
						} else {
							System.out.println("$ Client did not respond with SYN-ACK as expected, man");
							System.out.println("$ Connection failed, dawg");
							secretNumber = -1;
							socket = null;
							destIP = null;
							return false; // protocol broken, reset state variables
						}
					} else {
						System.out.println("$ Client did not respond with correct ACK number, homie");
						System.out.println("$ Potential threat of DDOS attack. Aborting connection, player");
						secretNumber = -1;
						socket = null;
						destIP = null;
						return false; // SYN cookie did not check out, host could be trying to leak our resources; restore state variables
					}
				} catch (SocketTimeoutException e) { // timeout, restore state variables, weakpoint in connection if chaotic network
					System.out.println("$ Socket timed out on SYN-ACK, dude");
					System.out.println("$ Connection failed, dawg");
					secretNumber = -1;
					socket = null;
					destIP = null;
					return false;
				} catch (IOException e) {
					System.out.println("$ Unexpected program deviation, brother (6)");
					System.out.println("$ Error message: " + e.getMessage());
					System.out.println("$ Connection failed, dawg");
					secretNumber = -1;
					socket = null;
					destIP = null;
					return false;
				}
			}
			clientSeqNum = 2; // set seq number moving forward
			connected = true; // we are now connected
			System.out.println("$ Connection succesful, cat\n");
		}
		return true;
	}
	
	// Output: data received from client
	public byte[] lilHomieReceiveSW() {
		if (!connected) {
			System.out.println("$ Slow your roll, bruh. You're not connected to anyone");
			System.out.println("$ Receive unsuccessful, player");
			return null;
		}
		byte[] data = new byte[0];
		int dataSize = Integer.MAX_VALUE; // initially set to start while loop, will be changed
		int nextDataIndex = 0; // pointer to next byte we are expecting to receive
		int expectedSeqNum = clientSeqNum;
		DatagramPacket rPacket = new DatagramPacket(new byte[DATAGRAM_PAYLOAD_SIZE], DATAGRAM_PAYLOAD_SIZE);
		while (nextDataIndex < dataSize) { // while we still haven't gotten all data
			try {
				socket.receive(rPacket); // receive packet, NO TIMEOUT
				String gotData = new String(rPacket.getData());
				String[] fields = gotData.split(",",-1); // split into fields
				if (rPacket.getAddress().equals(destIP)) { // is this from our connected host?
					if (rPacket.getPort() == destPort) { // is this from our connected host?
						if (!fields[6].equals("0")) { // did client request connection close? FIN has highest priority
							System.out.println("$ Client has requested a FIN, homeslice");
							System.out.println("$ Initiating connection tear down, gangsta");
							lilHomiePassiveClose(); // we did not initiate close, do a passive close
							return null;
						}
						if (fields[4].equals("0")) { // is it a RST?
							if (fields[6].equals("0")) { // old code, too hard to get rid of, will never go to else statement
								if (fields[2].equals(Integer.toString(expectedSeqNum))) { // is it the expected seq number?
									System.out.println("$ Packet was received from the client, my friend");
									System.out.println("$ Sending ACK, dude");
									if (data.length == 0) { // if its the first packet from this data transmission
										dataSize = Integer.valueOf(fields[7]); // set buffer to the size of total transmission
										data = new byte[dataSize];
									}
									byte[] thisPacketData = rPacket.getData(); // get data
									byte[] temp = new byte[thisPacketData.length-Integer.valueOf(fields[0])+3];
									for (int i = Integer.valueOf(fields[0])+3; i < thisPacketData.length; i++) {
										temp[i - Integer.valueOf(fields[0])+3] = thisPacketData[i]; // get data from payload
									}
									for (int i = 0; i < temp.length; i++) { // fill buffer with data from payload
										if (nextDataIndex+i < data.length) data[nextDataIndex+i] = temp[i];
									}
									nextDataIndex = nextDataIndex + temp.length; // progress pointer for next byte we don't have
									expectedSeqNum++;
									DatagramPacket ACK = packetize(rPacket.getAddress(), rPacket.getPort(), expectedSeqNum, 0, true, false, false, false, 0, null);
									socket.send(ACK); // send an ACK, we got the packet. if they don't receive the ACK they will timeout and resend their packet
								} else if (Integer.toString(expectedSeqNum-1).equals(fields[2])){ // if its the seq number we saw last time, they have resent because ACK was lost
									System.out.println("$ Identical packets received, presuming ACK was lost, man");
									System.out.println("$ Re-sending ACK, homie");
									DatagramPacket ACK = packetize(rPacket.getAddress(), rPacket.getPort(), expectedSeqNum, 0, true, false, false, false, 0, null);
									socket.send(ACK); // resend ACK
								} else {
									System.out.println("$ Packet received with sequence number that breaks protocol, dude");
									System.out.println("$ Receive failed, dog");
									return null; // quit transmission because protocol broken, should never happen
								}
							} else { // should never happen
								System.out.println("$ Client has requested a FIN, badass");
								System.out.println("$ Initiating connection tear down, gangsta");
								lilHomiePassiveClose();
								System.exit(0);
							}
						} else { // client requested reset
							System.out.println("$ Client has requested a forced connection tear-down, brah");
							System.out.println("$ The client is giving you cold shoulder, but you'll get back up on your feet, player");
							System.out.println("$ Forced connection tear down. You are now a single, independent server, my friend");
							connected = false;
							socket = null;
							destIP = null;
							return null; // restore state variables and leave
						}
					} else { // someone else is trying to talk to us, send them a RST in case they think they are actually connected
						System.out.println("$ A host you are not connected to is trying to communicate, mane");
						System.out.println("$ Sending RST packet requesting them to cease and desist, my friend");
						DatagramPacket backOffPacket = packetize(rPacket.getAddress(), rPacket.getPort(), 0, 0, false, true, false, false, 0, null);
						try {
							socket.send(backOffPacket);
						} catch (IOException e) {
							System.out.println("$ Unexpected program deviation, brother (2)");
							System.out.println("$ Error message: " + e.getMessage());
							System.out.println("$ Send failed, dog");
							return null;
						}
					}
				} else { // someone else is trying to talk to us, send them a RST in case they think they are actually connected
					System.out.println("$ A host you are not connected to is trying to communicate, mane");
					System.out.println("$ Sending RST packet requesting them to cease and desist, my friend");
					DatagramPacket backOffPacket = packetize(rPacket.getAddress(), rPacket.getPort(), 0, 0, false, true, false, false, 0, null);
					try {
						socket.send(backOffPacket);
					} catch (IOException e) {
						System.out.println("$ Unexpected program deviation, brother (2)");
						System.out.println("$ Error message: " + e.getMessage());
						System.out.println("$ Send failed, dog");
						return null;
					}
				}
			} catch (Exception e) {
				
			}
		}
		System.out.println("$ All data successfully received, brah");
		clientSeqNum = expectedSeqNum;
		return data; // we have read all the data they said they wanted to send, return data to application
	}
	
	// Output: True/False connection close was successful
		private boolean lilHomiePassiveClose() { // you did not initiate the close, but you need to gracefully exit all the same
			DatagramPacket FIN = packetize(destIP, destPort, 1, 0, false, false, false, true, 0, null); // send back a FIN
			while (true) {
				try {
					socket.send(FIN);
				} catch (IOException e) {
					System.out.println("$ Unexpected program deviation, brother");
					System.out.println("$ Error message: " + e.getMessage());
					System.out.println("$ Connection close failed, dag");
					return false;
				}
				try {
					socket.setSoTimeout(1000); // set timeout of 1 sec
				} catch (SocketException e) {
					System.out.println("$ Unexpected program deviation, brother (3)");
					System.out.println("$ Error message: " + e.getMessage());
					System.out.println("$ Connection close failed, dog");
					return false;
				}
				try {
					DatagramPacket rPacket = new DatagramPacket(new byte[DATAGRAM_PAYLOAD_SIZE], DATAGRAM_PAYLOAD_SIZE);
					socket.receive(rPacket);
					String data = new String(rPacket.getData());
					String[] fields = data.split(",",-1);
					if (fields[6].equals("1")) { // if FIN
						if (fields[3].equals("1")) { // if also ACK
							if (fields[1].equals("1")) { // and if ACK num correct
								System.out.println("$ It is now safe to tear down connection and deallocate resources, dog");
								socket = null;
								destIP = null;
								connected = false;
								System.out.println("$ Connection gracefully closed, homie");
								return true; // you did your job, tear everything down and leave
							} else {
								System.out.println("$ Server responded with unexpected ACK num, dog");
								System.out.println("$ Re-sending FIN, man");
							}
						} else {
							System.out.println("$ Unexpected break in protocol, brah");
							System.out.println("$ Re-sending FIN, my man");
						}
					} else {
						System.out.println("$ Server did not respond with FIN as expected, man");
						System.out.println("$ Re-sending FIN, dog");
					}
				} catch (SocketTimeoutException e) {
					System.out.println("$ Socket timed out on FIN, dude");
					System.out.println("$ Re-sending FIN, dog");
				} catch (Exception e) {
					System.out.println("$ Unexpected program deviation, brother (4)");
					System.out.println("$ Error message: " + e.getMessage());
					System.out.println("$ Connection close failed, dog");
					return false;
				}
			}
		}

		// Output: True/False connection close was successful
		public boolean lilHomieActiveClose() { // Client is initiating the close
			if (!connected) {
				System.out.println("$ You aren't connected, mane"); // doh
				return false;
			}
			DatagramPacket FIN = packetize(destIP, destPort, 0, 0, false, false, false, true, 0, null); // send FIN
			boolean ACKED = false;
			while (ACKED) { // get FIN ACKED
				try {
					socket.send(FIN);
				} catch (IOException e) {
					System.out.println("$ Unexpected program deviation, brother");
					System.out.println("$ Error message: " + e.getMessage());
					System.out.println("$ Connection close failed, dag");
					return false;
				}
				try {
					socket.setSoTimeout(1000);
				} catch (SocketException e) {
					System.out.println("$ Unexpected program deviation, brother (3)");
					System.out.println("$ Error message: " + e.getMessage());
					System.out.println("$ Connection failed, dog");
					return false;
				}
				try {
					DatagramPacket rPacket = new DatagramPacket(new byte[DATAGRAM_PAYLOAD_SIZE], DATAGRAM_PAYLOAD_SIZE);
					socket.receive(rPacket);
					String data = new String(rPacket.getData());
					String[] fields = data.split(",",-1);
					if (fields[6].equals("1")) {
						if (fields[3].equals("1")) {
							if (fields[1].equals("1")) {
								System.out.println("$ Server responded to connection close attempt with FIN-ACK, homie");
								System.out.println("$ Responding with ACK, bro");
								ACKED = true;
							} else {
								System.out.println("$ Server responded with unexpected ACK num, dog");
								System.out.println("$ Re-sending FIN, man");
							}
						} else {
							System.out.println("$ Server requested active close, mane");
							System.out.println("$ Defering to server, dog");
							return false;
						}
					} else {
						System.out.println("$ Server did not respond with FIN as expected, man");
						System.out.println("$ Re-sending FIN, dog");
					}
				} catch (SocketTimeoutException e) {
					System.out.println("$ Socket timed out on FIN, dude");
					System.out.println("$ Re-sending FIN, dog");
				} catch (Exception e) {
					System.out.println("$ Unexpected program deviation, brother (4)");
					System.out.println("$ Error message: " + e.getMessage());
					System.out.println("$ Connection close failed, dog");
					return false;
				}
			}
			DatagramPacket ACK = packetize(destIP, destPort, 1, 1, true, false, false, true, 0, null);
			while (true) { // once ACKED, you have to send a final FIN ACK. The passive closing host opposite you should be tearing everything down
							// and disconnecting by now. In this way, set a timer and when it expires, do the same
						// if you receive a message (aka, you don't timeout) you know its because they didn't receive one of your earlier
						// messages and they timed out themselves. Resend and set timer again. Closing-timer is 5 times longer than the regular timer
				try {
					socket.send(ACK);
				} catch (IOException e) {
					System.out.println("$ Unexpected program deviation, brother");
					System.out.println("$ Error message: " + e.getMessage());
					System.out.println("$ Connection close failed, dag");
					return false;
				}
				try {
					socket.setSoTimeout(5000);
				} catch (SocketException e) {
					System.out.println("$ Unexpected program deviation, brother (3)");
					System.out.println("$ Error message: " + e.getMessage());
					System.out.println("$ Connection failed, dog");
					return false;
				}
				try {
					DatagramPacket rPacket = new DatagramPacket(new byte[DATAGRAM_PAYLOAD_SIZE], DATAGRAM_PAYLOAD_SIZE);
					socket.receive(rPacket);
					System.out.println("$ Sending final FIN ACK, homie");
					System.out.println("$ Starting 5 second timer, yo");
				} catch (SocketTimeoutException e) { // we timed out, which means the connected host did not, and the connection is over
					System.out.println("$ Appropriate socket time out, dude");
					System.out.println("$ It is now safe to tear down connection and deallocate resources, dog");
					socket = null;
					destIP = null;
					connected = false;
					System.out.println("$ Connection gracefully closed, homie");
					return true; // shut shit down, and get out
				} catch (Exception e) {
					System.out.println("$ Unexpected program deviation, brother (4)");
					System.out.println("$ Error message: " + e.getMessage());
					System.out.println("$ Connection close attempt failed, dog");
					return false;
				}
			}
		}
		
		// Helper function to create packets, given field values, addr, port, and payload
		private DatagramPacket packetize(InetAddress destAddr, int port, int ackNum, int seqNum, boolean ACK, boolean RST, 
				boolean SYN, boolean FIN, int recvWindowSize, byte[] payload) {
			// constructs everything together as a string (fields separated by commas), and encapsulates in DatagramPacket
			String data = "" + ackNum + ",";
			data = data + seqNum + ",";
			data = data + (ACK ? "1," : "0,");
			data = data + (RST ? "1," : "0,");
			data = data + (SYN ? "1," : "0,");
			data = data + (FIN ? "1," : "0,");
			data = data + recvWindowSize + ",";
			int headerLength = data.getBytes().length;
			data = Integer.toString(headerLength) + "," + data;
			data = data + (payload != null ? new String(payload) : "");
			byte[] datagram = data.getBytes();
			return new DatagramPacket(datagram, datagram.length, destAddr, port);
		}
}
