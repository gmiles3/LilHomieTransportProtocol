// Server testing program that retrieves two messages (text and skull from ClientTester), prints them, then tries to
// 		read another message, but will perform a passive close once Client actively closes
public class ServerTester {

	public static void main(String[] args) {
		LilHomieServer server = new LilHomieServer(8081);
		boolean returnVal = server.lilHomieListen();
		if (returnVal) {
				System.out.println(new String(server.lilHomieReceiveSW())); // receive and print text
				System.out.println(new String(server.lilHomieReceiveSW())); // receive and print skull
				server.lilHomieReceiveSW(); // try to read again, end up blocking until active close from Client is received
		}
	}
}
