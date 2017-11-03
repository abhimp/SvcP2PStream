package com.mtp.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class Utils {

	public static Map<String, Object> parseMpd(String mpdFileName)
			throws ParserConfigurationException, SAXException, IOException {
		File inputFile = new File(mpdFileName);
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.parse(inputFile);
		doc.getDocumentElement().normalize();
		String segmentBase = ((Element) doc.getElementsByTagName("Initialization").item(0)).getAttribute("sourceURL");
		NodeList nodeList = doc.getElementsByTagName("Representation");

		int width = 0;
		int height = 0;
		int duration = 0;
		int numberOfSegments = 0;
		boolean first = true;
		List<Integer> idList = new ArrayList<>();
		List<Double> bwList = new ArrayList<>();
		List<List<String>> segmentUrls = new ArrayList<>();
		for (int itr = 0; itr < nodeList.getLength(); itr++) {
			Node node = nodeList.item(itr);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				Element element = (Element) node;
				Element segElement = (Element) element.getElementsByTagName("SegmentList").item(0);
				if (first) {
					width = Integer.parseInt(element.getAttribute("width"));
					height = Integer.parseInt(element.getAttribute("height"));
					duration = Integer.parseInt(segElement.getAttribute("duration"));
					numberOfSegments = segElement.getElementsByTagName("SegmentURL").getLength();
					first = false;
				}

				idList.add(Integer.parseInt(element.getAttribute("id")));
				bwList.add(Double.parseDouble(element.getAttribute("bandwidth")));
				List<String> tempSegUrls = new ArrayList<>();
				NodeList urlNodes = segElement.getElementsByTagName("SegmentURL");
				for (int jtr = 0; jtr < urlNodes.getLength(); jtr++) {
					Node urlNode = urlNodes.item(jtr);
					if (urlNode.getNodeType() == Node.ELEMENT_NODE) {
						tempSegUrls.add(((Element) urlNode).getAttribute("media"));
					}
				}
				segmentUrls.add(tempSegUrls);
			}
		}

		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put("height", height);
		resultMap.put("width", width);
		resultMap.put("duration", duration);
		resultMap.put("numberOfSegments", numberOfSegments);
		resultMap.put("idList", idList);
		resultMap.put("bwList", bwList);
		resultMap.put("segmentBase", segmentBase);
		resultMap.put("segmentUrls", segmentUrls);
		return resultMap;
	}

	public static void appendFile(File sourceFile, File destFile) throws IOException {
		FileInputStream fIn = null;
		FileOutputStream fOut = null;
		FileChannel source = null;
		FileChannel destination = null;
		try {
			fIn = new FileInputStream(sourceFile);
			source = fIn.getChannel();
			fOut = new FileOutputStream(destFile, true);
			destination = fOut.getChannel();
			destination.position(destination.size());
			long transfered = 0;
			long bytes = source.size();
			while (transfered < bytes) {
				transfered += source.transferTo(0, source.size(), destination);
				destination.position(transfered);
			}
		} finally {
			if (source != null) {
				source.close();
			} else if (fIn != null) {
				fIn.close();
			}
			if (destination != null) {
				destination.close();
			} else if (fOut != null) {
				fOut.close();
			}
		}
	}

	public static String readLogFileTail(String logFileName) throws IOException {
		RandomAccessFile readWriteFileAccess = new RandomAccessFile(logFileName, "rw");
		String currentLine = null;
		String nextLine = null;
		while ((nextLine = readWriteFileAccess.readLine()) != null) {
			currentLine = nextLine;
		}
		readWriteFileAccess.close();
		return currentLine;
	}

	/**
	 * Gets a string representing the pid of this program - Java VM
	 */
	public static String getPid() throws IOException, InterruptedException {

		List<String> commands = new ArrayList<>();
		commands.add("/bin/bash");
		commands.add("-c");
		commands.add("echo $PPID");
		ProcessBuilder pb = new ProcessBuilder(commands);
		Process pr = pb.start();
		pr.waitFor();
		if (pr.exitValue() == 0) {
			BufferedReader outReader = new BufferedReader(new InputStreamReader(pr.getInputStream()));
			return outReader.readLine().trim();
		} else {
			System.out.println("Error while getting PID");
			return "";
		}
	}

	public static void pipeStream(InputStream input, OutputStream output) throws IOException {
		byte buffer[] = new byte[1024];
		int numRead = 0;
		do {
			numRead = input.read(buffer);
			output.write(buffer, 0, numRead);
		} while (input.available() > 0);

		output.flush();
	}
}
