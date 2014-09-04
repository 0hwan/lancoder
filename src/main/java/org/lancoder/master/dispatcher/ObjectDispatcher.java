package org.lancoder.master.dispatcher;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.eclipse.jetty.util.BlockingArrayQueue;
import org.lancoder.common.Node;
import org.lancoder.common.RunnableService;
import org.lancoder.common.network.messages.ClusterProtocol;
import org.lancoder.common.network.messages.cluster.Message;
import org.lancoder.common.network.messages.cluster.TaskRequestMessage;
import org.lancoder.common.task.ClientTask;

public class ObjectDispatcher extends RunnableService {

	private DispatcherListener listener;
	private BlockingArrayQueue<DispatchItem> items = new BlockingArrayQueue<>(1);
	private boolean free = true;

	public ObjectDispatcher(DispatcherListener listener) {
		this.listener = listener;
	}

	public synchronized boolean isFree() {
		return free;
	}

	public synchronized boolean queue(DispatchItem item) {
		return this.items.offer(item);
	}

	private void dispatch(DispatchItem item) {
		free = false;
		ClientTask task = item.getTask();
		Node node = item.getNode();

		TaskRequestMessage trm = new TaskRequestMessage(task);
		boolean handled = false;
		try (Socket socket = new Socket()) {
			InetSocketAddress addr = new InetSocketAddress(node.getNodeAddress(), node.getNodePort());
			socket.connect(addr, 2000);
			ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
			out.flush();
			ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
			out.writeObject(trm);
			out.flush();
			Object o = in.readObject();
			if (o instanceof Message) {
				Message m = (Message) o;
				handled = m.getCode() == ClusterProtocol.TASK_ACCEPTED;
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (!handled) {
				listener.taskRefused(item);
			} else {
				listener.taskAccepted(item);
			}
			free = true;
		}
	}

	@Override
	public void run() {
		while (!close) {
			try {
				dispatch(items.take());
			} catch (InterruptedException e) {
			}
		}
	}

	@Override
	public void serviceFailure(Exception e) {
		// TODO Auto-generated method stub
	}

}