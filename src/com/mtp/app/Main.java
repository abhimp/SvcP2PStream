package com.mtp.app;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

public class Main {

	private static final String VIDEO_NAME = "factory-I-720p";
	private static final String VIDEO_DATA_PATH = "/home/ayush/CODES/Projects/JavaWorkspace/SvcP2PStream/resource/factory-I-720p/";
	private static final String OUTPUT_PATH_PREFIX = "/home/ayush/CODES/Projects/JavaWorkspace/SvcP2PStream/resource/factory-I-720p_out/";
	private static final int FPS_RATE = 25;
	private static final int SERVER_STARTUP_DELAY = 5000;
	private static final int SEGMENT_ENCODING_DELAY = 500;

	public static void main(String[] args) throws IOException, ParserConfigurationException, SAXException,
			NumberFormatException, InterruptedException {
		final String dataName = VIDEO_NAME + ".mpd";

		if (args.length == 0) {
			Server.run(VIDEO_DATA_PATH, dataName, FPS_RATE, SERVER_STARTUP_DELAY, SEGMENT_ENCODING_DELAY);
		} else {
			int clientId = Integer.parseInt(args[0]);
			String outputPath = OUTPUT_PATH_PREFIX + clientId + "/";
			Client.run(clientId, outputPath, dataName, VIDEO_NAME, FPS_RATE);
		}
	}
}
