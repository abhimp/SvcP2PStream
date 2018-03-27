package com.mtp.app;

import java.awt.AWTException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import net.tomp2p.utils.Pair;

public class Client {

	public static final Queue<Integer> stepsQueue = new LinkedList<>();
	public static final List<Integer> downloadedSegmentsList = new ArrayList<>();
	public static final String MERGE_SCRIPT = "./resource/script/svc_merge.py";
	public static final String PPSPP_SCRIPT = "./PyPPSPP/PyPPSPP/PyPPSPP.py";
	public static final String JSVM_DECODER = "./jsvm_9.19.5/bin/H264AVCDecoderLibTestStatic";

	@SuppressWarnings("unchecked")
	public static void run(int clientId, String trackerAddress, String outputpath, String videoName, int fpsRate)
			throws IOException, ParserConfigurationException, SAXException, InterruptedException {

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
		System.out.println("Start processing...\n" + LocalDateTime.now()
				+ ":\n========================================================\n" + "Video information:\n"
				+ "Video resolution:" + width + "x" + height + "\nLayerID is: " + layerIdList
				+ "\nBandwidth requirement for each layer: " + layerBWList + " bits/s" + "\nNumber of segments: "
				+ numberOfSegments + "\nDuration of each segment: " + duration + " frames\n"
				+ "========================================================");

		double downloadSpeed = 0;
		for (int itr = 0; itr < numberOfSegments; itr++) {
			int threshold = 0;
			int selectedLayer = 0;
			System.out.println(LocalDateTime.now() + ":\n==================================================\n"
					+ "Start handling segment " + itr + ", previous reference speed: " + (downloadSpeed / 8000)
					+ "KB/s");
			for (int jtr = 0; jtr < layerIdList.size(); jtr++) {
				int layerId = layerIdList.get(jtr);
				double layerBW = layerBWList.get(jtr);
				threshold += layerBW;
				System.out
						.println(LocalDateTime.now() + ": Threshold of " + layerId + ": " + threshold / 8000 + "KB/s");
				if (downloadSpeed >= threshold) {
					selectedLayer = layerId;
				} else if (jtr == 0) {
					selectedLayer = layerId;
				} else {
					break;
				}
			}
			System.out.println(LocalDateTime.now() + ": SelectedLayer: " + selectedLayer);
			if (Player.stopDownloadFlag) {
				System.out.println(LocalDateTime.now() + ": Stoping download...");
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

			String segementDecodedFileName = outputpath + String.valueOf(clientId) + "/" + videoName + ".seg" + itr
					+ ".yuv";
			String decodeCommand = JSVM_DECODER + " " + segmentFileName + " " + segementDecodedFileName;
			Runtime.getRuntime().exec(decodeCommand).waitFor();
			downloadedSegmentsList.add(itr);

			String outputFileName = outputpath + String.valueOf(clientId) + "/" + videoName + ".yuv";
			if (itr == 0) {
				if (Paths.get(outputFileName).toFile().exists()) {
					Paths.get(outputFileName).toFile().delete();
					Paths.get(outputFileName).toFile().createNewFile();
				}
			}
			Utils.appendFile(Paths.get(segementDecodedFileName).toFile(), Paths.get(outputFileName).toFile());
			System.out.println(LocalDateTime.now() + ": Finish handling segment " + itr
					+ "\n==================================================");
			int preSelectedLayer = 0;
			if (itr == 0) {
				preSelectedLayer = selectedLayer;
				Thread mplayerThread = new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							Thread.sleep(5000);
							OutputStream mplayerOutputStream = Player.playVideo(outputFileName, fpsRate, width, height);
							Thread mplayerControllerThread = new Thread(new Runnable() {

								@Override
								public void run() {
									try {
										Player.runMplayerController(outputFileName, duration, numberOfSegments,
												mplayerOutputStream);
									} catch (AWTException | IOException | InterruptedException e) {
										e.printStackTrace();
									}
								}
							});

							Thread.sleep(100);
							mplayerControllerThread.start();
						} catch (IOException | InterruptedException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
					}
				});
				mplayerThread.start();
			} else {
				stepsQueue.add(layerIdList.indexOf(selectedLayer) - layerIdList.indexOf(preSelectedLayer));
				preSelectedLayer = selectedLayer;
			}
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

		double downloadSpeed = 0;
		List<String> fileList = new ArrayList<>();
		for (int itr = 0; (itr < layerIdList.size()) && (layerIdList.get(itr) <= selectedLayer); itr++) {
			String segmentLayerName = segmentUrls.get(itr).get(segNum);
			double startTime = System.currentTimeMillis() / 1000.00;

			String clientPeerLogFileName = outputpath + String.valueOf(clientId) + "/logs/" + segmentLayerName
					+ "ClientPeer.log";
			String[] clientCommand = new String[] { "python3", PPSPP_SCRIPT, "--tracker", trackerAddress, "--filename",
					outputpath + String.valueOf(clientId) + "/" + segmentLayerName, "--swarmid",
					hashListMap.get(segmentLayerName), "--filesize", sizeListMap.get(segmentLayerName), "--port",
					String.valueOf(6778 + clientId * 20 + segNum * 4 + itr), "--logger", clientPeerLogFileName };
			ProcessBuilder pb = new ProcessBuilder();
			pb.command(clientCommand);
			pb.start();

			while (true) {
				String currentLogText = Utils.readLogFileTail(clientPeerLogFileName);
				if (currentLogText != null && currentLogText.contains("Have/Missing")) {
					int missing = Integer
							.parseInt(currentLogText.split("Have/Missing ")[1].split(";")[0].split("/")[1]);
					if (missing == 0) {
						break;
					}
				}
				continue;
			}
			double endTime = System.currentTimeMillis() / 1000.00;
			long segmentLayerSize = Integer.parseInt(sizeListMap.get(segmentLayerName));
			double timeInterval = endTime - startTime;
			downloadSpeed = segmentLayerSize / timeInterval;
			System.out.println(LocalDateTime.now() + ": FileName: " + segmentLayerName + " download complete...");
			fileList.add(outputpath + String.valueOf(clientId) + "/" + segmentLayerName);
		}

		return new Pair<>(downloadSpeed, fileList);
	}
}
