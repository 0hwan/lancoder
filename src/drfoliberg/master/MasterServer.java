package drfoliberg.master;

import java.io.IOException;
import java.net.ServerSocket;

public class MasterServer extends Thread {

	private Master master;

	public MasterServer(Master master) {
		this.master = master;
	}

	public void run() {
		try {
			ServerSocket server = new ServerSocket(1337);
			while (true) {
				new HandleMaster(server.accept(), master).start();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}