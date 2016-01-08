import java.io.*;
import java.net.*;

// Lil Homie Transport Layer Protocol Client (unidirectional, stop-and-wait)
// Client actively connects to an IP/port combo for transfering data
// Client does not read data, client only reads control messages from server
public class LilHomieClient {

	private DatagramSocket socket; // communication socket
	private InetAddress destIP; // what IP we want to talk to
	private int destPort; // what port on the IP we want to talk to
	private int srcPort; // what port we want to bind to
	private boolean connected; // is this client currently connected to another host? (1 host max)
	private int currentSeqNum; // what's the current sequence number I am using


	// headers are 29 bytes
	// this means an actual payload could be 995 bytes in a 1024 byte packet
	private final int RAW_DATA_SIZE = 995;
	private final int DATAGRAM_PAYLOAD_SIZE = 1024;

	// construct client object
	public LilHomieClient(int srcPort) {
		this.srcPort = srcPort;
		connected = false;
	}
	
	// Input: address, and port
	// Output: True/False if connected to address on this port
	public boolean lilHomieConnect(InetAddress addr, int port) {
		if (port < 0) {
			System.out.println("$ Invalid port number, son");
			System.out.println("$ Connection failed, dog");
			return false;
		}
		System.out.println("\n$ Wassup, son\n$ Let's see if we can get you connected...");
		destIP = addr;
		destPort = port;
		try {
			socket = new DatagramSocket(srcPort); // attempt to create socket on source port
		} catch (SocketException e) { // if there's an issue, we abort the connection attempt
			System.out.println("$ Unexpected program deviation, brother (1)");
			System.out.println("$ Error message: " + e.getMessage());
			System.out.println("$ Connection failed, dog");
			socket = null; //always be sure to erase important state variables if connection is to be destroyed
			destIP = null; // ""
			return false;
		}
		int expectedACK = 0; // we are expecting an ACK num of 0
		int serverSeqNum = 0; // server sequence number should also be 0
		while (!connected) { // while a connection has not formed
			boolean SYNreceived = false;
			while (!SYNreceived) { // while SYN not received
				DatagramPacket sPacket = packetize(destIP, destPort, 0, 0, false, false, true, false, 0, null); // construct to-send packet
				expectedACK = 1;
				try {
					socket.send(sPacket); //attempt to send packet
				} catch (IOException e) { // if there's an issue, abort attempt
					System.out.println("$ Unexpected program deviation, brother (2)");
					System.out.println("$ Error message: " + e.getMessage());
					System.out.println("$ Connection failed, dog");
					socket = null;
					destIP = null;
					return false;
				}
				try {
					socket.setSoTimeout(1000); // set timeout of 1 sec
				} catch (SocketException e) { // if there's an issue, abort attempt
					System.out.println("$ Unexpected program deviation, brother (3)");
					System.out.println("$ Error message: " + e.getMessage());
					System.out.println("$ Connection failed, dog");
					socket = null;
					destIP = null;
					return false;
				}
				try {
					DatagramPacket rPacket = new DatagramPacket(new byte[DATAGRAM_PAYLOAD_SIZE], DATAGRAM_PAYLOAD_SIZE);
					socket.receive(rPacket); // thread will block on this function until a packet is received in rPacket, or an exception comes up
					String data = new String(rPacket.getData()); // if we made it here, it means we didn't timeout; get data from packet
					String[] fields = data.split(",",-1); // split data in comma separated fields
					if (fields[1].equals(Integer.toString(expectedACK))) { // if ACK num is what we expect
						if (fields[5].equals("1")) { // if it is in fact an ACK
							if (fields[4].equals("0")) { // if a reset signal is caught
								System.out.println("$ Server responded to connection attempt with SYN, homie"); // if we made it here, all conditions were met, reply with ACK
								System.out.println("$ Responding with ACK, bro");
								serverSeqNum = Integer.valueOf(fields[2]);
								SYNreceived = true;
							} else { // if reset, abort
								System.out.println("$ Server requested RST, mane");
								System.out.println("$ Connection failed, dog");
								socket = null;
								destIP = null;
								return false;
							}
						} else { // if not a SYN packet we received, abort
							System.out.println("$ Server did not respond with SYN as expected, man");
							System.out.println("$ Connection failed, dog");
							socket = null;
							destIP = null;
							return false;
						}
					} else { // if incorrect ACK num, abort
						System.out.println("$ Server did not respond with correct ACK number, homie");
						System.out.println("$ Connection failed, dog");
						socket = null;
						destIP = null;
						return false;
					}
				} catch (SocketTimeoutException e) { // socket timed out, re-attempt to connect
					System.out.println("$ Socket timed out on SYN, dude");
					System.out.println("$ Re-attempting to connect, dog");
				} catch (Exception e) { // anything else, abort
					System.out.println("$ Unexpected program deviation, brother (4)");
					System.out.println("$ Error message: " + e.getMessage());
					System.out.println("$ Connection failed, dog");
					socket = null;
					destIP = null;
					return false;
				}
			}
			// SYN received, send ACK
			DatagramPacket sPacket = packetize(destIP, destPort, serverSeqNum+1, 1, true, false, true, false, 0, null); // construct ACK
			expectedACK++;
			currentSeqNum = 2;
			try {
				socket.send(sPacket); // atempt to send
			} catch (IOException e) {
				System.out.println("$ Unexpected program deviation, brother (5)");
				System.out.println("$ Error message: " + e.getMessage());
				System.out.println("$ Connection failed, dog");
				socket = null;
				destIP = null;
				return false;
			}
			connected = true; // downside to being a client, we have to assume they receive this ACK and we have to assume we are connected
							 // this is the weak point of connecting, and in a chaotic network, will often cause connections to be unsuccessful
			System.out.println("$ Connection successful, cat\n");
		}
		return true; // if it turns out later that the ACK wasn't received, we'll know because the server will send us another SYN cookie
	}

	// Input: data in byte array that we wish to send
	// Output: True/False did it send successfully?
	public boolean lilHomieSendSW(byte[] data) {
		if (!connected) { // no one to send to
			System.out.println("$ Slow your roll, bruh. You're not connected to anyone");
			System.out.println("$ Send unsuccessful, player");
			return false;
		}
		if (data == null) { // nothing to send
			System.out.println("$ Hey, watch it! You passed me null data, jabroni");
			System.out.println("$ Send unsuccessful, player");
			return false;
		}
		int dataSize = data.length; // how much data are they trying to send?
		int nextDataIndex = 0; // pointer that refers to the next byte I am going to send from the data; once this equals dataSize, we are done
		if (dataSize == 0) { // still nothing to send
			System.out.println("$ What's the point in sending no data, broheim?");
			System.out.println("$ Send unsuccessful, player");
			return false;
		}
		int numPackets = (int) Math.ceil(((double)dataSize) / ((double)RAW_DATA_SIZE)); // if we can divide data into payloads of 995 bytes, how many packets will we need?
		while (numPackets > 0) { // while we still have packets to send
			byte[] toSend = new byte[(dataSize >= RAW_DATA_SIZE ? RAW_DATA_SIZE : dataSize)]; // set right size of byte array to be sent; either 995 exactly, or its the last remaining in data buffer
			for (int k = nextDataIndex; k < nextDataIndex + toSend.length ; k++) {
				toSend[k-nextDataIndex] = data[k]; // not the easiest thing to understand, but fills the payload we want to send with the data from the buffer
			}
			DatagramPacket sPacket = packetize(destIP, destPort, 0, currentSeqNum, false, false, false, false, dataSize, toSend); // wrap payload into packet
			boolean ACKED = false;
			while (!ACKED) { // while this packet hasn't been acknowledged by server
				try {
					socket.send(sPacket); // attempt to send
				} catch (IOException e) {
					System.out.println("$ Unexpected program deviation, brother (2)");
					System.out.println("$ Error message: " + e.getMessage());
					System.out.println("$ Send failed, dog");
					return false;
				}
				try {
					socket.setSoTimeout(1000); // set timeout of 1 sec
				} catch (SocketException e) {
					System.out.println("$ Unexpected program deviation, brother (3)");
					System.out.println("$ Error message: " + e.getMessage());
					System.out.println("$ Send failed, dog");
					return false;
				}
				try {
					DatagramPacket rPacket = new DatagramPacket(new byte[DATAGRAM_PAYLOAD_SIZE], DATAGRAM_PAYLOAD_SIZE);
					socket.receive(rPacket); // block here until timeout or reception
					String gotData = new String(rPacket.getData()); // if you're here, you received something
					String[] fields = gotData.split(",",-1); // split packet in fields
					if (rPacket.getAddress().equals(destIP)) { // was it from the IP i'm connected to?
						if (rPacket.getPort() == destPort) { // was it form the port of the IP i'm connected to?
							if (!fields[6].equals("0")) { // is this a FIN? FIN's have top priority
								System.out.println("$ Server has requested a FIN, homeslice");
								System.out.println("$ Initiating connection tear down, gangsta");
								lilHomiePassiveClose(); // you were called to close, you did not initiate (slightly diff logic)
								return false; // the connection is closed, so it doesn't matter what you wanted to send
							}
							if (fields[1].equals(Integer.toString(currentSeqNum+1))) { // is this the seq number we expect
								if (fields[3].equals("1")) { //is it an ACK?
									if (fields[4].equals("0")) { // is it a RST?
										if (fields[6].equals("0")) { // is it a FIN? (redudant because of convenience. I added it in early code, and it would screw all the curly braces up to remove)
											System.out.println("$ Packet was ACKed by the server, my friend"); // we met all necessary conditions
											System.out.println("$ Sending next packet, dude");
											ACKED = true; // which means this was indeed the appropriate ACK
											currentSeqNum++; // increase seq number
											numPackets--; // decrease number of successful packets to-be-transmitted (once 0, we're done)
											dataSize = dataSize - toSend.length; // subtract the remaining data amount we have to send
											nextDataIndex = nextDataIndex + toSend.length; // increase the pointer to the first byte of data we haven't touched yet
										} else {
											System.out.println("$ Server has requested a FIN, badass"); // this code will never be reached (how sad)
											System.out.println("$ Initiating connection tear down, gangsta");
											lilHomiePassiveClose();
											return false;
										}
									} else { // do to the fact that the client had to assume a connection at the end of connect, this is the defense mechanism.
										// the server will send a RST to you if the client had assumed a connection while the server never received the last ACK.
										// this is to force the client to abort the pseudo-connection for security reasons, and so connection can be attempted again,
										// otherwise the client might be have allocated resources that will never be used, or reserving its port and ignoring other
										// programs because its clinging on to its connection
										// the implication here, is that programmers using this method should perform a check that their connection didn't suddenly unravel
										System.out.println("$ Whoa, it appears the server did not correctly establish a connection with you, smooth criminal");
										System.out.println("$ The server is giving you cold shoulder, but you'll get back up on your feet, player");
										System.out.println("$ Forced connection tear down. You are now a single, independent client, my friend");
										connected = false; // hahahahaha, you weren't actually connected
										socket = null;
										destIP = null;
										return false;
									}
								} else { // server and you are not on the same page. if this happens, it's a subtle error I missed
									System.out.println("$ Server did not respond with an ACK as expected, brother");
									System.out.println("$ Send failed, dog");
									return false;
								}
							} else { // common mistakes with seq numbers are caused by chaotic network. just resend it
								System.out.println("$ Server did not respond with correct sequence number, homie");
								System.out.println("$ Potential network problems, resending previous packet, dog");
							}
						} else { // someone else wants a piece of the action
							// send them a RST to tell them you're busy
							// they may, like the case above, actually believe they are connected to you, so this will help them out
							System.out.println("$ A host you are not connected to is trying to communicate, mane");
							System.out.println("$ Sending RST packet requesting them to cease and desist, my friend");
							DatagramPacket backOffPacket = packetize(rPacket.getAddress(), rPacket.getPort(), 0, 0, false, true, false, false, 0, null);
							try {
								socket.send(backOffPacket);
							} catch (IOException e) {
								System.out.println("$ Unexpected program deviation, brother (2)");
								System.out.println("$ Error message: " + e.getMessage());
								System.out.println("$ Send failed, dog");
								return false;
							}
						}
					} else { // same thing
						System.out.println("$ A host you are not connected to is trying to communicate, mane");
						System.out.println("$ Sending RST packet requesting them to cease and desist, my friend");
						DatagramPacket backOffPacket = packetize(rPacket.getAddress(), rPacket.getPort(), 0, 0, false, true, false, false, 0, null);
						try {
							socket.send(backOffPacket);
						} catch (IOException e) {
							System.out.println("$ Unexpected program deviation, brother (2)");
							System.out.println("$ Error message: " + e.getMessage());
							System.out.println("$ Send failed, dog");
							return false;
						}
					}
				} catch (SocketTimeoutException e) { // socket timed out waiting for ACK, resend data
					System.out.println("$ Socket timed out waiting for ACK, dude");
					System.out.println("$ Re-sending the packet, dog");
				} catch (Exception e) {
					System.out.println("$ Unexpected program deviation, brother (4)");
					System.out.println("$ Error message: " + e.getMessage());
					System.out.println("$ Send failed, dog");
					return false;
				}
			}
		}
		System.out.println("$ Data sent successfully, mane");
		return true; // if you reached here you sent all the packets you needed to transfer all the data, and they were all ACKed
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
