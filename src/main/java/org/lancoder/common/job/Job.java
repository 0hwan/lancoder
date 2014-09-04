package org.lancoder.common.job;

import java.io.File;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.lancoder.common.file_components.FileInfo;
import org.lancoder.common.file_components.streams.Stream;
import org.lancoder.common.status.JobState;
import org.lancoder.common.status.TaskState;
import org.lancoder.common.task.ClientTask;
import org.lancoder.common.task.Task;
import org.lancoder.common.task.audio.AudioTask;
import org.lancoder.common.task.audio.ClientAudioTask;
import org.lancoder.common.task.video.ClientVideoTask;
import org.lancoder.common.task.video.VideoTask;

/**
 * A job is the whole process of taking the source file, splitting it if necessary, encoding it and merge back all
 * tracks. Tasks will be dispatched to nodes by the master.
 * 
 * @author justin
 * 
 */
public class Job implements Comparable<Job>, Serializable {

	private static final long serialVersionUID = -3817299446490049451L;

	private String jobId;
	private String jobName;
	private JobState jobStatus = JobState.JOB_TODO;
	private int lengthOfTasks;
	private long lengthOfJob;
	private int frameCount;
	private double frameRate;
	private int priority;
	/**
	 * Output path of this job, relative to absolute shared directory
	 */
	private String outputFolder;
	/**
	 * Filename for final output file relative to outputFolder
	 */
	private String outputFileName;
	/**
	 * The folder in which to store the parts before muxing
	 */
	private String partsFolderName;
	private String sourceFile;

	private ArrayList<Task> tasks = new ArrayList<>();
	private ArrayList<ClientTask> clientTasks = new ArrayList<>();

	public Job(String jobName, String inputFile, int lengthOfTasks, String encodingOutputFolder, FileInfo fileInfo) {
		this.jobName = jobName;
		this.lengthOfTasks = lengthOfTasks;
		this.lengthOfJob = fileInfo.getDuration();
		this.frameRate = fileInfo.getMainVideoStream().getFrameRate();
		this.partsFolderName = "parts"; // TODO Why would this change ? Perhaps move to constant.

		// Estimate the frame count from the frame rate and length
		this.frameCount = (int) Math.floor((lengthOfJob / 1000 * frameRate));
		// Get source's filename
		sourceFile = inputFile;
		// Set output's filename
		this.outputFileName = String.format("%s.mkv", FilenameUtils.removeExtension(sourceFile));
		// Get /sharedFolder/LANcoder/jobsOutput/jobName/ (without the shared folder)
		File relativeEncodingOutput = FileUtils.getFile(encodingOutputFolder, jobName);
		this.outputFolder = relativeEncodingOutput.getPath();

		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] byteArray = md.digest((inputFile + jobName + System.currentTimeMillis()).getBytes());
			String result = "";
			for (int i = 0; i < byteArray.length; i++) {
				result += Integer.toString((byteArray[i] & 0xff) + 0x100, 16).substring(1);
			}
			this.jobId = result;
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			// even if the algorithm is not available, don't crash
			this.jobId = String.valueOf(System.currentTimeMillis());
		}
	}

	public String getSourceFile() {
		return sourceFile;
	}

	public int getTaskCount() {
		return this.tasks.size();
	}

	/**
	 * Get the tasks associated to this stream.
	 * 
	 * @param stream
	 *            The stream to look for
	 * @return An ArrayList of related tasks
	 */
	public ArrayList<ClientTask> getTasksForStream(Stream stream) {
		ArrayList<ClientTask> tasks = new ArrayList<>();
		// for (int i = 0; i < this.getTasks().size(); i++) {
		// ClientTask task = this.getTasks().get(i);
		// if (task.getStream().equals(stream)) {
		// tasks.add(task);
		// }
		// }
		return tasks;
	}

	/**
	 * Returns next task to encode. Changes Job status if job is not started yet.
	 * 
	 * @return The task or null if no task is available
	 */
	public synchronized ClientVideoTask getNextVideoTask() {
		if (getTaskRemainingCount() == 0) {
			return null;
		}
		if (this.getJobStatus() == JobState.JOB_TODO) {
			// TODO move this to job manager
			this.setJobStatus(JobState.JOB_COMPUTING);
		}
		for (ClientVideoTask task : this.getClientVideoTasks()) {
			if (task.getProgress().getTaskState() == TaskState.TASK_TODO) {
				return task;
			}
		}
		return null;
	}

	/**
	 * Counts if necessary the tasks currently not processed. A task being processed by a node counts as processed.
	 * 
	 * @return The count of tasks left to dispatch
	 */
	public synchronized int getTaskRemainingCount() {
		switch (this.getJobStatus()) {
		case JOB_COMPLETED:
			return 0;
		case JOB_TODO:
			return this.tasks.size();
		default:
			int count = 0;
			for (Task task : this.tasks) {
				if (task.getProgress().getTaskState() == TaskState.TASK_TODO) {
					++count;
				}
			}
			return count;
		}
	}

	public ArrayList<AudioTask> getAudioTasks() {
		ArrayList<AudioTask> tasks = new ArrayList<>();
		for (Task task : this.tasks) {
			if (task instanceof AudioTask) {
				tasks.add((AudioTask) task);
			}
		}
		return tasks;
	}

	public ArrayList<VideoTask> getVideoTasks() {
		ArrayList<VideoTask> tasks = new ArrayList<>();
		for (Task task : this.tasks) {
			if (task instanceof VideoTask) {
				tasks.add((VideoTask) task);
			}
		}
		return tasks;
	}

	public ArrayList<ClientAudioTask> getClientAudioTasks() {
		ArrayList<ClientAudioTask> tasks = new ArrayList<>();
		for (ClientTask task : this.clientTasks) {
			if (task instanceof ClientAudioTask) {
				tasks.add((ClientAudioTask) task);
			}
		}
		return tasks;
	}

	public ArrayList<ClientVideoTask> getClientVideoTasks() {
		ArrayList<ClientVideoTask> tasks = new ArrayList<>();
		for (ClientTask task : this.clientTasks) {
			if (task instanceof ClientVideoTask) {
				tasks.add((ClientVideoTask) task);
			}
		}
		return tasks;
	}

	public ArrayList<ClientTask> getClientTasks() {
		return clientTasks;
	}

	public ArrayList<Task> getTasks() {
		return tasks;
	}

	/**
	 * Compare job accordingly to priority, tasks remaining and job length.
	 * 
	 * @return 1 if this is bigger, -1 otherwise
	 */
	public int compareTo(Job other) {
		if (this.priority != other.priority) {
			return Integer.compare(this.priority, other.priority);
		} else if (this.getTaskRemainingCount() != other.getTaskRemainingCount()) {
			return Integer.compare(this.getTaskRemainingCount(), other.getTaskRemainingCount());
		} else {
			return Long.compare(this.getLengthOfJob(), other.getLengthOfJob());
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof Job)) {
			return false;
		}
		Job other = (Job) obj;
		return other.getJobId().equals(this.getJobId());
	}

	public JobState getJobStatus() {
		return jobStatus;
	}

	public void setJobStatus(JobState jobStatus) {
		this.jobStatus = jobStatus;
	}

	public long getFrameCount() {
		return frameCount;
	}

	public double getFrameRate() {
		return frameRate;
	}

	public int getLengthOfTasks() {
		return lengthOfTasks;
	}

	public void setLengthOfTasks(int lengthOfTasks) {
		this.lengthOfTasks = lengthOfTasks;
	}

	public long getLengthOfJob() {
		return lengthOfJob;
	}

	public void setLengthOfJob(long lengthOfJob) {
		this.lengthOfJob = lengthOfJob;
	}

	public String getJobId() {
		return jobId;
	}

	public void setJobId(String jobId) {
		this.jobId = jobId;
	}

	public String getJobName() {
		return jobName;
	}

	public void setJobName(String jobName) {
		this.jobName = jobName;
	}

	public String getOutputFolder() {
		return outputFolder;
	}

	public void setOutputFolder(String outputFolder) {
		this.outputFolder = outputFolder;
	}

	public String getOutputFileName() {
		return outputFileName;
	}

	public void setOutputFileName(String outputFileName) {
		this.outputFileName = outputFileName;
	}

	public String getPartsFolderName() {
		return partsFolderName;
	}

	public void setPartsFolderName(String partsFolderName) {
		this.partsFolderName = partsFolderName;
	}

	public ArrayList<Stream> getStreams() {
		// TODO Auto-generated method stub
		return null;
	}
}