package org.lancoder.worker.converter.audio;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.lancoder.common.config.Config;
import org.lancoder.common.exceptions.MissingThirdPartyException;
import org.lancoder.common.file_components.streams.AudioStream;
import org.lancoder.common.job.RateControlType;
import org.lancoder.common.task.audio.ClientAudioTask;
import org.lancoder.common.utils.TimeUtils;
import org.lancoder.ffmpeg.FFmpegReader;
import org.lancoder.worker.converter.Converter;
import org.lancoder.worker.converter.ConverterListener;

public class AudioWorkThread extends Converter<ClientAudioTask> {

	private static Pattern timePattern = Pattern.compile("time=([0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{2,3})");
	private FFmpegReader ffmpeg = new FFmpegReader();

	public AudioWorkThread(ConverterListener listener, String absoluteSharedFolder, String tempEncodingFolder,
			Config config) {
		super(listener, absoluteSharedFolder, tempEncodingFolder, config);
	}

	private ArrayList<String> getArgs(ClientAudioTask task) {
		ArrayList<String> args = new ArrayList<>();
		AudioStream outStream = task.getStreamConfig().getOutStream();
		AudioStream inStream = task.getStreamConfig().getOrignalStream();

		String absoluteInput = FileUtils.getFile(absoluteSharedDir, inStream.getRelativeFile()).getAbsolutePath();
		String streamMapping = String.format("0:%d", inStream.getIndex());
		String channelDisposition = String.valueOf(outStream.getChannels().getCount());
		String sampleRate = String.valueOf(outStream.getSampleRate());

		String[] baseArgs = new String[] { config.getFFmpegPath(), "-i", absoluteInput, "-vn", "-sn", "-map",
				streamMapping, "-ac", channelDisposition, "-ar", sampleRate, "-c:a", outStream.getCodec().getEncoder() };
		Collections.addAll(args, baseArgs);
		switch (outStream.getCodec()) {
		case VORBIS:
			String rateControlString = outStream.getRateControlType() == RateControlType.CRF ? "-q:a" : "-b:a";
			String rate = outStream.getRateControlType() == RateControlType.CRF ? String.valueOf(outStream.getRate())
					: String.format("%dk", outStream.getRate());
			args.add(rateControlString);
			args.add(rate);
			break;
		// TODO support other codecs
		default:
			// TODO unknown codec exception
			break;
		}
		// Meta-data mapping
		args.add("-map_metadata");
		args.add(String.format("0:s:%d", inStream.getIndex()));
		args.add(taskTempOutputFile.getPath());
		return args;
	}

	private boolean moveFile() {
		try {
			FileUtils.moveFile(taskTempOutputFile, taskFinalFile);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	@Override
	public void stop() {
		super.stop();
		ffmpeg.stop();
	}

	@Override
	public void serviceFailure(Exception e) {
		e.printStackTrace();
		// TODO
	}

	@Override
	protected void start() {
		listener.taskStarted(task);
		setFiles();
		boolean success = false;
		createDirs();
		ArrayList<String> args = getArgs(task);
		try {
			success = ffmpeg.read(args, this, true) && moveFile();
		} catch (MissingThirdPartyException e) {
			e.printStackTrace();
		} finally {
			if (success) {
				listener.taskCompleted(task);
			} else {
				listener.taskFailed(task);
			}
			destroyTempFolder();
		}
	};

	@Override
	public void onMessage(String line) {
		Matcher m = null;
		m = timePattern.matcher(line);
		if (m.find()) {
			task.getProgress().update(TimeUtils.getMsFromString(m.group(1)) / 1000);
		}
	}
}
