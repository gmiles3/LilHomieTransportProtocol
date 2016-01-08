import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class FxAserver {
	
	private static int srcPort;
	private static MySmartBuffer dataBuffer = new MySmartBuffer();
	private static LilHomieServer server = new LilHomieServer(srcPort);

	public static void main(String[] args) throws IOException {
		if (args.length != 3) {
			System.out.println("Usage is: java FxAserver X A P");
			System.exit(0);
		}
		srcPort = Integer.valueOf(args[0]);
		
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		boolean connected = server.lilHomieListen();
		if (connected) {
			while (true) {
				if (!in.ready()) {
					byte[] data = server.lilHomieReceiveSW();
					dataBuffer.writeTo(data);
					byte[] res = dataBuffer.smartRead();
					if (res != null) {
						System.out.println(new String(res));
					}
				} else {
					String input = in.readLine();
					if (input.equals("terminate".toLowerCase())) {
						boolean close = server.lilHomieActiveClose();
						if (close) {
							System.exit(0);
						}
					}
				}
			}
//			ServerReceiveThread srt = new ServerReceiveThread();
//			Thread t1 = new Thread(srt);
//			ServerPrintThread spt = new ServerPrintThread();
//			Thread t2 = new Thread(spt);
//			t1.start();
//			t2.start();
//			while (true) {
//				if (in.ready()) {
//					String input = in.readLine();
//					if (input.equals("terminate".toLowerCase())) {
//						boolean close = server.lilHomieActiveClose();
//						if (close) {
//							System.exit(0);
//						}
//					}
//				}
//			}
		}
		
	}
	
	

//		public static void main(String[] args) throws UnknownHostException {
//			boolean returnVal = server.lilHomieListen(5000);
//			if (returnVal) {
//					byte[] data = server.lilHomieReceiveSW();
//					server.lilHomieActiveClose();
//					System.out.println(new String(data));
//			}
//		}

		private static class MySmartBuffer {
			private byte[] buffer;
			private boolean isStale;

			public MySmartBuffer() {
				buffer = null;
				isStale = true;
			}

			public void writeTo(byte[] data) {
				buffer = data;
				isStale = false;
			}

			public byte[] smartRead() {
				if (!isStale) {
					isStale = true;
					return buffer;
				} else {
					return null;
				}
			}
		}
}
