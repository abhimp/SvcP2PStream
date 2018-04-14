package com.mtp.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

public class Seeder {
	public static final String PPSPP_SCRIPT = "./PyPPSPP/PyPPSPP/PyPPSPP.py";

	@SuppressWarnings("unchecked")
	public static void run(String trackerAddress, String datapath, String mpdFileName, int fpsRate, long startupDelay,
			long encodingDelay) throws IOException, ParserConfigurationException, SAXException, InterruptedException {

		System.out.println(LocalDateTime.now() + ": Server starting in " + startupDelay / 1000 + "s... Please wait!");
		Thread.sleep(startupDelay);

		String hashFileName = datapath + "hash_list.txt";
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

		Map<String, Object> parseResultMap = Utils.parseMpd(datapath + mpdFileName);
		// long segmentDurationDelay = (int) parseResultMap.get("duration") * 1000 / fpsRate;
		int numberOfSegments = (int) parseResultMap.get("numberOfSegments");
		List<Integer> layerIdList = (List<Integer>) parseResultMap.get("idList");
		List<List<String>> segmentUrls = (List<List<String>>) parseResultMap.get("segmentUrls");
		for (int itr = 0; itr < numberOfSegments; itr++) {
			System.out.println(LocalDateTime.now() + ": Encoding segment " + itr + 1 + "...");
			// Thread.sleep(encodingDelay);
			System.out.println("\n==================================================\n" + LocalDateTime.now()
					+ ": Starting upload of segment " + itr);
			for (int jtr = 0; jtr < layerIdList.size(); jtr++) {
				System.out.println(
						LocalDateTime.now() + ": Uploading segment " + itr + 1 + " layer " + layerIdList.get(jtr) + "...");
				String segmentLayerName = segmentUrls.get(jtr).get(itr);

				String seederPeerLogFileName = datapath + "logs/" + segmentLayerName + "SeederPeer.log";
				File logFile = new File(seederPeerLogFileName);
				try {
					Files.deleteIfExists(logFile.toPath());
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				
				String[] seederCommand = new String[] { "python3", PPSPP_SCRIPT, "--tracker", trackerAddress,
						"--filename", datapath + segmentLayerName, "--swarmid", hashListMap.get(segmentLayerName),
						"--filesize", sizeListMap.get(segmentLayerName), "--port", String.valueOf(2000 + itr*4 + jtr),
						"--logger", seederPeerLogFileName };
				System.out.println(LocalDateTime.now() + ": Starting seeder " + segmentLayerName + " on Port: " + String.valueOf(2000 + itr*6 + jtr));
				ProcessBuilder pb = new ProcessBuilder();
				pb.command(seederCommand);
				pb.start();
			}

			if (itr + 1 < numberOfSegments) {
				System.out.println("\n==================================================\n" + LocalDateTime.now()
						+ ": Preparing segment " + (itr + 2) + "...");
				// Thread.sleep(segmentDurationDelay);
			}
		}

		 System.out.println(LocalDateTime.now() + ": End of Segments");
	}
}
