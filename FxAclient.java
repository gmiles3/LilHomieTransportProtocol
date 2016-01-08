import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;

public class FxAclient {

	private static int srcPort;
	private static InetAddress destAddr;
	private static int destPort;
	private static LilHomieClient client = new LilHomieClient(srcPort);

	public static void main(String[] args) throws IOException {
		if (args.length != 3) {
			System.out.println("Usage is: java FxAclient X A P");
			System.exit(0);
		}
		srcPort = Integer.valueOf(args[0]);
		destAddr = InetAddress.getByName(args[1]);
		destPort = Integer.valueOf(args[2]);
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		while (true) {
			if (in.ready()) {
				String input = in.readLine();
				if (input.equals("connect".toLowerCase())) {
					boolean connect = client.lilHomieConnect(destAddr, destPort);
					if (connect) {
						break;
					}
				} else {
					System.out.println("$ You must CONNECT first");
				}
			}
		}
		while (true) {
			if (in.ready()) {
				String input = in.readLine();
				if (input.equals("terminate".toLowerCase())) {
					boolean close = client.lilHomieActiveClose();
					if (close) {
						System.exit(0);
					}
				} else if (input.substring(0, 4).equals("post".toLowerCase())) {
					String toPost = input.substring(4);
					client.lilHomieSendSW(toPost.getBytes());
				} else {
					System.out.println("$ You may either POST <string> or TERMINATE");
				}
			}
		}
	}

}
