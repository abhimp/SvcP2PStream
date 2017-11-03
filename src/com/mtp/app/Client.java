package com.mtp.app;

import java.awt.AWTException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.mtp.p2p.ClientPeer;

import net.tomp2p.utils.Pair;

public class Client {

	public static final Queue<Integer> stepsQueue = new LinkedList<>();
	public static final List<Integer> downloadedSegmentsList = new ArrayList<>();
	public static final String MUX_SCRIPT = "/home/ayush/CODES/Projects/JavaWorkspace/SvcP2PStream/resource/script/svc_mux.py";

	@SuppressWarnings("unchecked")
	public static void run(int clientId, String outputpath, String mpdFileName, String videoName, int fpsRate)
			throws IOException, ParserConfigurationException, SAXException, InterruptedException {
		ClientPeer client = new ClientPeer(clientId);
		Path path = Paths.get(outputpath + mpdFileName);
		Files.write(path, client.find(mpdFileName));
		System.out.println("Name:" + mpdFileName + " >> Download complete...");

		Map<String, Object> parseResultMap = Utils.parseMpd(outputpath + mpdFileName);
		int width = (int) parseResultMap.get("width");
		int height = (int) parseResultMap.get("height");
		int duration = (int) parseResultMap.get("duration");
		int numberOfSegments = (int) parseResultMap.get("numberOfSegments");
		String segmentBaseUrl = (String) parseResultMap.get("segmentBase");
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
		String segmentBaseFileName = null;
		if (segmentBaseUrl != null) {
			Pair<Double, String> downloadResult = downloadSegmentBase(client, outputpath, segmentBaseUrl);
			downloadSpeed = downloadResult.element0();
			segmentBaseFileName = downloadResult.element1();
		}

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

			Pair<Double, List<String>> downloadResult = downloadSegment(client, outputpath, selectedLayer, layerIdList,
					segmentUrls, itr);
			downloadSpeed = downloadResult.element0();
			List<String> segmentFileList = downloadResult.element1();
			downloadedSegmentsList.add(itr);
			String segementFileName = outputpath + videoName + "_seg" + itr + ".264";
			List<String> commandList = new ArrayList<>(Arrays.asList("python", MUX_SCRIPT, segementFileName));
			if (segmentBaseFileName != null) {
				commandList.add(segmentBaseFileName);
			}

			commandList.addAll(segmentFileList);
			String command = String.join(" ", commandList);
			Runtime.getRuntime().exec(command).waitFor();
			String outputFileName = outputpath + videoName + ".264";
			if (itr == 0) {
				if (Paths.get(outputFileName).toFile().exists()) {
					Paths.get(outputFileName).toFile().delete();
					Paths.get(outputFileName).toFile().createNewFile();
				}
			}

			Utils.appendFile(Paths.get(segementFileName).toFile(), Paths.get(outputFileName).toFile());
			System.out.println(LocalDateTime.now() + ": Finish handling segment " + itr
					+ "\n==================================================");
			int preSelectedLayer = 0;
			if (itr == 0) {
				preSelectedLayer = selectedLayer;
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
			} else {
				stepsQueue.add(layerIdList.indexOf(selectedLayer) - layerIdList.indexOf(preSelectedLayer));
				preSelectedLayer = selectedLayer;
			}
		}
		
		System.out.println(LocalDateTime.now() + ": Shutting down Client in 5s");
		Thread.sleep(5000);
		client.shutdown();
	}

	/**
	 * helper method to download SegmentBase
	 * 
	 * @param client
	 * @param segmentBaseUrl2
	 * @throws IOException
	 * @throws InterruptedException
	 * 
	 */
	private static Pair<Double, String> downloadSegmentBase(ClientPeer client, String outputpath, String segmentBaseUrl)
			throws IOException, InterruptedException {
		double startTime = System.currentTimeMillis() / 1000.00;
		byte[] segmentBaseBytes = client.find(segmentBaseUrl);
		double endTime = System.currentTimeMillis() / 1000.00;
		long segmentLayerSize = segmentBaseBytes.length;
		double timeInterval = endTime - startTime;
		double downloadSpeed = segmentLayerSize * 8 / timeInterval;
		Path path = Paths.get(outputpath + segmentBaseUrl);
		Files.write(path, segmentBaseBytes);
		System.out.println(LocalDateTime.now() + ": FileName: " + segmentBaseUrl + " download complete...");
		return new Pair<>(downloadSpeed, outputpath + segmentBaseUrl);
	}

	/**
	 * helper method to download layers of given segment
	 * 
	 */
	private static Pair<Double, List<String>> downloadSegment(ClientPeer client, String outputpath, int selectedLayer,
			List<Integer> layerIdList, List<List<String>> segmentUrls, int segNum)
			throws IOException, InterruptedException {
		double downloadSpeed = 0;
		List<String> fileList = new ArrayList<>();
		for (int itr = 0; (itr < layerIdList.size()) && (layerIdList.get(itr) <= selectedLayer); itr++) {
			String segmentLayerName = segmentUrls.get(itr).get(segNum);
			double startTime = System.currentTimeMillis() / 1000.00;
			byte[] segmentLayerBytes = client.find(segmentLayerName);
			double endTime = System.currentTimeMillis() / 1000.00;
			long segmentLayerSize = segmentLayerBytes.length;
			double timeInterval = endTime - startTime;
			downloadSpeed = segmentLayerSize * 8 / timeInterval;
			Path path = Paths.get(outputpath + segmentLayerName);
			Files.write(path, segmentLayerBytes);
			System.out.println(LocalDateTime.now() + ": FileName: " + segmentLayerName + " download complete...");
			fileList.add(outputpath + segmentLayerName);
		}

		return new Pair<>(downloadSpeed, fileList);
	}
}
