package com.mtp.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.mtp.p2p.ServerPeer;

public class Server {

	@SuppressWarnings("unchecked")
	public static void run(String datapath, String mpdFileName, int fpsRate, long startupDelay, long encodingDelay)
			throws IOException, ParserConfigurationException, SAXException, InterruptedException {
		ServerPeer server = new ServerPeer();

		System.out.println(LocalDateTime.now() + ": Server starting in " + startupDelay / 1000 + "s... Please wait!");
		Thread.sleep(startupDelay);
		server.store(mpdFileName, Files.readAllBytes(Paths.get(datapath + mpdFileName)));
		Map<String, Object> parseResultMap = Utils.parseMpd(datapath + mpdFileName);
		String segmentBaseUrl = (String) parseResultMap.get("segmentBase");
		server.store(segmentBaseUrl, Files.readAllBytes(Paths.get(datapath + segmentBaseUrl)));
		long segmentDurationDelay = (int) parseResultMap.get("duration") * 1000 / fpsRate;
		int numberOfSegments = (int) parseResultMap.get("numberOfSegments");
		List<Integer> layerIdList = (List<Integer>) parseResultMap.get("idList");
		List<List<String>> segmentUrls = (List<List<String>>) parseResultMap.get("segmentUrls");
		for (int itr = 0; itr < numberOfSegments; itr++) {
			System.out.println(LocalDateTime.now() + ": Encoding segment " + itr + "...");
//			Thread.sleep(encodingDelay);
			System.out.println("\n==================================================\n" + LocalDateTime.now()
					+ ": Starting upload of segment " + itr);
			for (int jtr = 0; jtr < layerIdList.size(); jtr++) {
				System.out.println(
						LocalDateTime.now() + ": Uploading segment " + itr + " layer " + layerIdList.get(jtr) + "...");
				String segmentLayerName = segmentUrls.get(jtr).get(itr);
				server.store(segmentLayerName, Files.readAllBytes(Paths.get(datapath + segmentLayerName)));
			}

			if (itr + 1 < numberOfSegments) {
				System.out.println("\n==================================================\n" + LocalDateTime.now()
						+ ": Preparing segment " + (itr + 1) + "...");
//				Thread.sleep(segmentDurationDelay);
			}
		}

//		System.out.println(LocalDateTime.now() + ": End of Video");
//		System.out.println(LocalDateTime.now() + ": Shutting down Server in 20s");
//		Thread.sleep(20000);
//		server.shutdown();
	}
}
