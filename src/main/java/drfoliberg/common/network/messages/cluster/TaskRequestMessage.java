package drfoliberg.common.network.messages.cluster;

import drfoliberg.common.network.Routes;
import drfoliberg.common.task.video.VideoEncodingTask;

public class TaskRequestMessage extends Message {

	private static final long serialVersionUID = -994042578899999534L;

	public VideoEncodingTask task;

	/**
	 * Sent from master to workers
	 * 
	 * @param task
	 *            The task to request to the worker
	 */
	public TaskRequestMessage(VideoEncodingTask task) {
		super(Routes.ADD_TASK);
		this.task = task;
	}
}
