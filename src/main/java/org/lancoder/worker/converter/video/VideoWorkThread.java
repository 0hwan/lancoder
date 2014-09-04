package org.lancoder.worker.converter.video;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lancoder.common.exceptions.MissingDecoderException;
import org.lancoder.common.exceptions.MissingFfmpegException;
import org.lancoder.common.exceptions.WorkInterruptedException;
import org.lancoder.common.file_components.streams.VideoStream;
import org.lancoder.common.network.Cause;
import org.lancoder.common.task.video.ClientVideoTask;
import org.lancoder.common.utils.FileUtils;
import org.lancoder.common.utils.TimeUtils;
import org.lancoder.ffmpeg.FFmpegReader;
import org.lancoder.worker.converter.Converter;
import org.lancoder.worker.converter.ConverterListener;

public class VideoWorkThread extends Converter {

	private static String OS = System.getProperty("os.name").toLowerCase();

	private ClientVideoTask task;
	private static Pattern currentFramePattern = Pattern.compile("frame=\\s+([0-9]+)");
	private static Pattern fpsPattern = Pattern.compile("fps=\\s+([0-9]+)");
	private static Pattern missingDecoder = Pattern.compile("Error while opening encoder for output stream");

	public VideoWorkThread(ClientVideoTask task, ConverterListener listener) {
		this.task = task;
		this.listener = listener;
		absoluteSharedDir = new File(listener.getConfig().getAbsoluteSharedFolder());
		taskTempOutputFile = FileUtils.getFile(listener.getConfig().getTempEncodingFolder(), task.getTempFile());
		taskTempOutputFolder = new File(taskTempOutputFile.getParent());
		taskFinalFolder = FileUtils.getFile(absoluteSharedDir, new File(task.getTempFile()).getParentFile().getPath());
	}

	private static boolean isWindows() {
		return (OS.indexOf("win") >= 0);
	}

	public void encodePass(String startTimeStr, String durationStr) throws MissingFfmpegException,
			MissingDecoderException, WorkInterruptedException {
		VideoStream inStream = task.getStreamConfig().getOrignalStream();
		File inputFile = new File(absoluteSharedDir, inStream.getRelativeFile());
		String mapping = String.format("0:%d", inStream.getIndex());
		// Get parameters from the task and bind parameters to process
		String[] baseArgs = new String[] { "ffmpeg", "-ss", startTimeStr, "-t", durationStr, "-i",
				inputFile.getAbsolutePath(), "-sn", "-force_key_frames", "0", "-an", "-map", mapping, "-c:v", "libx264" };
		ArrayList<String> ffmpegArgs = new ArrayList<>();
		// Add base args to process builder
		Collections.addAll(ffmpegArgs, baseArgs);

		ffmpegArgs.addAll(task.getRateControlArgs());
		ffmpegArgs.addAll(task.getPresetArg());

		// output file and pass arguments
		String outFile = taskTempOutputFile.getAbsoluteFile().toString();
		if (task.getStepCount() > 1) {
			// Add pass arguments
			ffmpegArgs.add("-pass");
			ffmpegArgs.add(String.valueOf(task.getProgress().getCurrentStep()));
			if (task.getProgress().getCurrentStepIndex() != task.getStepCount()) {
				ffmpegArgs.add("-f");
				ffmpegArgs.add("rawvideo");
				ffmpegArgs.add("-y");
				// Change output file to null
				outFile = isWindows() ? "NUL" : "/dev/null";
			}
		}
		ffmpegArgs.add(outFile);
		FFmpegReader ffmpeg = new FFmpegReader();
		// Start process in task output directory (log and mtrees pass files generated by ffmpeg)
		ffmpeg.read(ffmpegArgs, this, true, taskTempOutputFolder);
	}

	@Override
	public void run() {
		boolean success = false;
		try {
			listener.workStarted(task);
			System.out.println("WORKER WORK THREAD: Executing task " + task.getTaskId());
			// use start and duration for ffmpeg legacy support
			long durationMs = task.getEncodingEndTime() - task.getEncodingStartTime();
			String startTimeStr = TimeUtils.getStringFromMs(task.getEncodingStartTime());
			String durationStr = TimeUtils.getStringFromMs(durationMs);
			createDirs();

			task.getProgress().start();
			int currentStep = 1;
			while (currentStep <= task.getStepCount()) {
				System.err.printf("Encoding pass %d of %d\n", task.getProgress().getCurrentStepIndex(),
						task.getStepCount());
				encodePass(startTimeStr, durationStr);
				task.getProgress().completeStep();
				currentStep++;
			}
			System.err.println("transcoding");
			success = transcodeToMpegTs();
			System.err.println("transcoding finished");
		} catch (MissingFfmpegException | MissingDecoderException e) {
			listener.nodeCrash(new Cause(e, "unknown", true));
		} catch (WorkInterruptedException e) {
			System.err.println("WORKER: stopping work");
		} finally {
			if (success) {
				listener.workCompleted(task);
			} else {
				listener.workFailed(task);
			}
		}
	}

	private boolean transcodeToMpegTs() {
		File destination = new File(absoluteSharedDir, task.getTempFile());
		// TODO handle robust handling of progress and errors
		try {
			ProcessBuilder ffmpeg = new ProcessBuilder();
			String[] baseArgs = new String[] { "ffmpeg", "-i", taskTempOutputFile.getAbsolutePath(), "-f", "mpegts",
					"-c", "copy", "-bsf:v", "h264_mp4toannexb", destination.getAbsolutePath() };
			ArrayList<String> args = new ArrayList<>();
			Collections.addAll(args, baseArgs);
			ffmpeg.command(args);
			System.err.println(args.toString()); // DEBUG

			Process transcoder = ffmpeg.start();
			transcoder.waitFor();
			if (transcoder.exitValue() != 0) {
				System.err.println("Transcoding from mkv to mpegts appears to have failed !");
				return false;
			}
			FileUtils.givePerms(destination, false);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} catch (InterruptedException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	@Override
	public void serviceFailure(Exception e) {
		this.listener.nodeCrash(null);
		// TODO
	}

	@Override
	public void onMessage(String line) {
		double speed = -1;
		long units = -1;
		Matcher m = currentFramePattern.matcher(line);
		if (m.find()) {
			units = Long.parseLong(m.group(1));
		}
		m = fpsPattern.matcher(line);
		if (m.find()) {
			speed = Double.parseDouble(m.group(1));
		}
		m = missingDecoder.matcher(line);
		if (m.find()) {
			System.err.println("Missing decoder !");
		} else if (units != -1 && speed != -1) {
			task.getProgress().update(units, speed);
		} else if (units != -1) {
			task.getProgress().update(units);
		}
	}
}