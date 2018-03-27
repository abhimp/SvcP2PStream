package com.mtp.app;

import java.awt.AWTException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Player {

	public static boolean stopDownloadFlag = false;
	private static final Logger LOGGER = Logger.getLogger(Player.class.getName());

	public static OutputStream playVideo(String videoFileName, int fpsRate, int width, int height) throws IOException {
		LOGGER.info(LocalDateTime.now() + ": Opening mplayer...");
		String videoLogFileName = videoFileName + ".log";
		ProcessBuilder pb = new ProcessBuilder();
		pb.redirectOutput(new File(videoLogFileName));
		pb.redirectInput();
		pb.command(new String[] {
				"mplayer", "-demuxer", "rawvideo", "-rawvideo", "w=" + Integer.toString(width) + ":h="
						+ Integer.toString(height) + ":format=i420:fps=" + Integer.toString(fpsRate),
				"-slave", videoFileName });
		Process p = pb.start();
		return p.getOutputStream();
	}

	public static void runMplayerController(String videoFileName, int frameCount, int numberOfSegments,
			OutputStream mplayerOutStream) throws AWTException, IOException, InterruptedException {
		// timeInterval defines the print frequency of the frame number in the
		// terminal
		double timeInterval = 0.4;
		double frameStep = timeInterval * 25;
		String videoLogFileName = videoFileName + ".log";
		String controllerLogFileName = videoFileName + ".controller.log";

		// Configure the logger with handler and formatter
		FileHandler fh = new FileHandler(controllerLogFileName);
		LOGGER.addHandler(fh);
		SimpleFormatter formatter = new SimpleFormatter();
		fh.setFormatter(formatter);

		// Start reading player log at set timeInterval
		int tmpIdx = 1;
		while (true) {
			String currentLogText = Utils.readLogFileTail(videoLogFileName);
			if (currentLogText == null) {
				continue;
			} else if (currentLogText.contains("PAUSE") && !currentLogText.contains("=====  PAUSE  =====\rV:")) {
				Thread.sleep((long) timeInterval * 1000);
				continue;
			} else if (currentLogText.contains("Exiting")) {
				LOGGER.info(LocalDateTime.now() + ": " + currentLogText);
				LOGGER.info(LocalDateTime.now() + ": Exiting mplayer...\n" + "==================================");
				stopDownloadFlag = true;
				break;
			} else if (currentLogText.contains("[vdpau]") || currentLogText.contains("no prescaling applied")) {
				continue;
			} else {
				LOGGER.info(LocalDateTime.now() + ": " + currentLogText);
				int frameIdx = 0;
				try {
					frameIdx = parseFrameIndex(currentLogText);
				} catch (ParseException | NumberFormatException e) {
					LOGGER.warning(e.getMessage());
					continue;
				}

				// Check if the next segment finish download, if not pause the
				// video at the before the current segment end
				if (frameIdx < frameCount * tmpIdx - 3 * frameStep
						&& frameIdx >= frameCount * tmpIdx - (4 * frameStep)) {
					if (Client.downloadedSegmentsList.size() < (tmpIdx + 1)
							&& Client.downloadedSegmentsList.size() < numberOfSegments) {
						LOGGER.info(LocalDateTime.now() + ": Downloaded segments: " + Client.downloadedSegmentsList);
						commandMplayerToPauseOrPlay(mplayerOutStream);
						LOGGER.info(LocalDateTime.now() + ": Pause the video and wait for segment download");
						while (true) {
							Thread.sleep(100);
							if (Client.downloadedSegmentsList.size() >= (tmpIdx + 1)) {
								commandMplayerToPauseOrPlay(mplayerOutStream); // Resume
																				// video
								LOGGER.info(LocalDateTime.now() + ": Resume playing the video");
								break;
							}
						}
					}
					Thread.sleep((long) timeInterval * 1000);
				}
				if (frameIdx >= frameCount * tmpIdx && frameIdx < frameCount * tmpIdx + frameStep) {
					LOGGER.info(LocalDateTime.now() + ": CurrentFrame is: " + frameIdx);
					if (!Client.stepsQueue.isEmpty()) {
						tmpIdx++;
						Client.stepsQueue.remove();
						Thread.sleep((long) timeInterval * 1000);
					} else {
						LOGGER.info(LocalDateTime.now() + ": Closing mplayer controller...");
						break;
					}
					LOGGER.info(LocalDateTime.now() + ": New segment count index" + tmpIdx);
				}
			}
		}

		mplayerOutStream.close();
	}

	private static int parseFrameIndex(String currentLogText) throws ParseException {
		String[] textElements = currentLogText.split("/");
		if (textElements.length != 2) {
			throw new ParseException(LocalDateTime.now() + ": error in frame index parse\n"
					+ "last line in log text is:" + currentLogText, 0);
		}
		String[] tokens = textElements[0].split(" ");
		return Integer.parseInt(tokens[tokens.length - 1]);
	}

	public static void commandMplayerToPauseOrPlay(OutputStream mplayerOutStream) throws IOException {
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(mplayerOutStream));
		writer.write("pause\n");
		writer.flush();
	}
}
