package com.mtp.app;

import java.awt.AWTException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import net.tomp2p.utils.Pair;

public class Client {

	public static final Queue<Integer> stepsQueue = new LinkedList<>();
	public static final List<Integer> downloadedSegmentsList = new ArrayList<>();
	public static final String MERGE_SCRIPT = "./resource/script/svc_merge.py";
	public static final String PPSPP_SCRIPT = "./PyPPSPP/PyPPSPP/PyPPSPP.py";
	public static final String JSVM_DECODER = "./jsvm_9.19.5/bin/H264AVCDecoderLibTestStatic";
	private static final Logger LOGGER = Logger.getLogger(Client.class.getName());

	@SuppressWarnings("unchecked")
	public static void run(int clientId, String trackerAddress, String outputpath, String videoName, int fpsRate)
			throws IOException, ParserConfigurationException, SAXException, InterruptedException {
		// Configure the logger with handler and formatter
		FileHandler fh = new FileHandler(outputpath + String.valueOf(clientId) + "/" + videoName + ".client.log");
		LOGGER.addHandler(fh);
		SimpleFormatter formatter = new SimpleFormatter();
		fh.setFormatter(formatter);

		String hashFileName = outputpath + "metadata/hash_list.txt";
		Map<String, String> hashListMap = new HashMap<>();
		Map<String, String> sizeListMap = new HashMap<>();
		File file = new File(hashFileName);
		FileReader fileReader = new FileReader(file);
		BufferedReader bufferedReader = new BufferedReader(fileReader);
		String hashline;
		while ((hashline = bufferedReader.readLine()) != null) {
			String hashtokens[] = hashline.split("\t");
			hashListMap.put(hashtokens[0], hashtokens[1]);
			sizeListMap.put(hashtokens[0], hashtokens[2]);
		}
		bufferedReader.close();

		String mpdFileName = outputpath + "metadata/" + videoName + ".mpd";
		String initFileName = outputpath + "metadata/" + videoName + ".init.svc";
		Map<String, Object> parseResultMap = Utils.parseMpd(mpdFileName);
		int width = (int) parseResultMap.get("width");
		int height = (int) parseResultMap.get("height");
		int duration = (int) parseResultMap.get("duration");
		int numberOfSegments = (int) parseResultMap.get("numberOfSegments");
		List<Integer> layerIdList = (List<Integer>) parseResultMap.get("idList");
		List<Double> layerBWList = (List<Double>) parseResultMap.get("bwList");
		List<List<String>> segmentUrls = (List<List<String>>) parseResultMap.get("segmentUrls");
		LOGGER.info("Start processing... \n========================================================\n" 
				+ "Video information:\n" + "Video resolution:" + width + "x" + height + "\nLayerID is: " + layerIdList
				+ "\nBandwidth requirement for each layer: " + layerBWList + " bits/s" + "\nNumber of segments: "
				+ numberOfSegments + "\nDuration of each segment: " + duration + " frames\n"
				+ "========================================================");

		double downloadSpeed = 0;
		for (int itr = 0; itr < numberOfSegments; itr++) {
			int threshold = 0;
			int selectedLayer = 0;
			LOGGER.info(":\n==================================================\n"
					+ "Start handling segment " + itr + ", previous reference speed: " + (downloadSpeed / 1000)
					+ "KB/s");
			for (int jtr = 0; jtr < layerIdList.size(); jtr++) {
				int layerId = layerIdList.get(jtr);
				double layerBW = layerBWList.get(jtr);
				threshold += layerBW;
				LOGGER.info(": Threshold of " + layerId + ": " + threshold / 8000 + "KB/s");
				if (downloadSpeed >= threshold / 8) {
					selectedLayer = layerId;
				} else if (jtr == 0) {
					selectedLayer = layerId;
				} else {
					break;
				}
			}
			LOGGER.info(": SelectedLayer: " + selectedLayer);
			if (Player.stopDownloadFlag) {
				LOGGER.info(": Stoping download...");
				break;
			}

			Pair<Double, List<String>> downloadResult = downloadSegment(trackerAddress, clientId, outputpath,
					selectedLayer, layerIdList, segmentUrls, hashListMap, sizeListMap, itr);
			downloadSpeed = downloadResult.element0();
			List<String> segmentFileList = downloadResult.element1();
			String segmentFileName = outputpath + String.valueOf(clientId) + "/" + videoName + ".seg" + itr + ".264";
			List<String> commandList = new ArrayList<>(Arrays.asList("python", MERGE_SCRIPT, segmentFileName));
			commandList.add(initFileName);
			commandList.addAll(segmentFileList);
			String mergeCommand = String.join(" ", commandList);
			Runtime.getRuntime().exec(mergeCommand).waitFor();

			final int seg = itr;
			final int curLayer = selectedLayer;
			Thread decoderThread = new Thread(new Runnable() {
				@Override
				public void run() {
					String segementDecodedFileName = outputpath + String.valueOf(clientId) + "/" + videoName + ".seg" + seg
							+ ".yuv";
					String decodeCommand = JSVM_DECODER + " " + segmentFileName + " " + segementDecodedFileName;
					try {
						Runtime.getRuntime().exec(decodeCommand).waitFor();
					} catch (InterruptedException | IOException e2) {
						e2.printStackTrace();
					}
					downloadedSegmentsList.add(seg);
					
					String outputFileName = outputpath + String.valueOf(clientId) + "/" + videoName + ".yuv";
					if (seg == 0) {
						if (Paths.get(outputFileName).toFile().exists()) {
							Paths.get(outputFileName).toFile().delete();
							try {
								Paths.get(outputFileName).toFile().createNewFile();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
					try {
						Utils.appendFile(Paths.get(segementDecodedFileName).toFile(), Paths.get(outputFileName).toFile());
					} catch (IOException e2) {
						e2.printStackTrace();
					}
					LOGGER.info(": Finish handling segment " + seg
							+ "\n==================================================");
					int preSelectedLayer = 0;
					if (seg == 0) {
						preSelectedLayer = curLayer;
//						Thread mplayerThread = new Thread(new Runnable() {
//							@Override
//							public void run() {
//								try {
//									Thread.sleep(5000);
//									OutputStream mplayerOutputStream = Player.playVideo(outputFileName, fpsRate, width, height);
//									Thread mplayerControllerThread = new Thread(new Runnable() {
//
//										@Override
//										public void run() {
//											try {
//												Player.runMplayerController(outputFileName, duration, numberOfSegments,
//														mplayerOutputStream);
//											} catch (AWTException | IOException | InterruptedException e) {
//												e.printStackTrace();
//											}
//										}
//									});
//
//									Thread.sleep(100);
//									mplayerControllerThread.start();
//								} catch (IOException | InterruptedException e1) {
//									// TODO Auto-generated catch block
//									e1.printStackTrace();
//								}
//							}
//						});
//						mplayerThread.start();
					} else {
						stepsQueue.add(layerIdList.indexOf(curLayer) - layerIdList.indexOf(preSelectedLayer));
						preSelectedLayer = curLayer;
					}
				}
			});
			
			decoderThread.start();
		}
	}

	/**
	 * helper method to download layers of given segment
	 * 
	 * @param selectedLayer
	 * @param outputpath
	 * 
	 * @param sizeListMap
	 * @param hashListMap
	 * 
	 */
	private static Pair<Double, List<String>> downloadSegment(String trackerAddress, int clientId, String outputpath,
			int selectedLayer, List<Integer> layerIdList, List<List<String>> segmentUrls,
			Map<String, String> hashListMap, Map<String, String> sizeListMap, int segNum)
			throws IOException, InterruptedException {

		List<Double> downloadSpeed = new ArrayList<>();
		List<String> fileList = new ArrayList<>();
		layerIdList.parallelStream().filter(layer -> layer <= selectedLayer).forEach(layer -> {
			String segmentLayerName = segmentUrls.get(layer).get(segNum);
			double startTime = System.currentTimeMillis() / 1000.00;

			String clientPeerLogFileName = outputpath + String.valueOf(clientId) + "/logs/" + segmentLayerName
					+ "ClientPeer.log";
			File logFile = new File(clientPeerLogFileName);
			try {
				Files.deleteIfExists(logFile.toPath());
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			
			String[] clientCommand = new String[] { "python3", PPSPP_SCRIPT, "--tracker", trackerAddress, "--filename",
					outputpath + String.valueOf(clientId) + "/" + segmentLayerName, "--swarmid",
					hashListMap.get(segmentLayerName), "--filesize", sizeListMap.get(segmentLayerName), "--port",
					String.valueOf(6778 + clientId * 20 + segNum * 4 + layer), "--logger", clientPeerLogFileName };
			ProcessBuilder pb = new ProcessBuilder();
			pb.command(clientCommand);
			try {
				pb.start();
			} catch (IOException e) {
				e.printStackTrace();
			}

			while (true) {
				String currentLogText;
				try {
					currentLogText = Utils.readLogFileTail(clientPeerLogFileName);
					if (currentLogText != null && currentLogText.contains("Have/Missing")) {
						int missing = Integer
								.parseInt(currentLogText.split("Have/Missing ")[1].split(";")[0].split("/")[1]);
						if (missing == 0) {
							break;
						}
					}
					continue;
				} catch (IOException e) {
					e.printStackTrace();
					break;
				}
			}
			
			double endTime = System.currentTimeMillis() / 1000.00;
			long segmentLayerSize = Integer.parseInt(sizeListMap.get(segmentLayerName));
			double timeInterval = endTime - startTime;
			double layerDownloadSpeed = segmentLayerSize / timeInterval;
			LOGGER.info(": FileName: " + segmentLayerName + " download complete...");
			downloadSpeed.add(layerDownloadSpeed);
		});
		
		layerIdList.stream().filter(layer -> layer <= selectedLayer).forEach(layer -> {
			fileList.add(outputpath + String.valueOf(clientId) + "/" + segmentUrls.get(layer).get(segNum));
		});
		

//		for (int itr = 0; (itr < layerIdList.size()) && (layerIdList.get(itr) <= selectedLayer); itr++) {
//			String segmentLayerName = segmentUrls.get(itr).get(segNum);
//			double startTime = System.currentTimeMillis() / 1000.00;
//
//			String clientPeerLogFileName = outputpath + String.valueOf(clientId) + "/logs/" + segmentLayerName
//					+ "ClientPeer.log";
//			String[] clientCommand = new String[] { "python3", PPSPP_SCRIPT, "--tracker", trackerAddress, "--filename",
//					outputpath + String.valueOf(clientId) + "/" + segmentLayerName, "--swarmid",
//					hashListMap.get(segmentLayerName), "--filesize", sizeListMap.get(segmentLayerName), "--port",
//					String.valueOf(6778 + clientId * 20 + segNum * 4 + itr), "--logger", clientPeerLogFileName };
//			ProcessBuilder pb = new ProcessBuilder();
//			pb.command(clientCommand);
//			pb.start();
//
//			while (true) {
//				String currentLogText = Utils.readLogFileTail(clientPeerLogFileName);
//				if (currentLogText != null && currentLogText.contains("Have/Missing")) {
//					int missing = Integer
//							.parseInt(currentLogText.split("Have/Missing ")[1].split(";")[0].split("/")[1]);
//					if (missing == 0) {
//						break;
//					}
//				}
//				continue;
//			}
//			double endTime = System.currentTimeMillis() / 1000.00;
//			long segmentLayerSize = Integer.parseInt(sizeListMap.get(segmentLayerName));
//			double timeInterval = endTime - startTime;
//			downloadSpeed = segmentLayerSize / timeInterval;
//			LOGGER.info(": FileName: " + segmentLayerName + " download complete...");
//			fileList.add(outputpath + String.valueOf(clientId) + "/" + segmentLayerName);
//		}

		return new Pair<>(downloadSpeed.stream().max((a, b) -> Double.compare(a, b)).get(), fileList);
	}
}
