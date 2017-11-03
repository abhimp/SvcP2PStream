/**
 * 
 */
package com.mtp.p2p;

import java.io.IOException;

import net.tomp2p.dht.FutureGet;
import net.tomp2p.dht.PeerBuilderDHT;
import net.tomp2p.dht.PeerDHT;
import net.tomp2p.p2p.PeerBuilder;
import net.tomp2p.peers.Number160;
import net.tomp2p.storage.Data;

/**
 * @author ayush
 *
 */
public abstract class BasePeer {

	private PeerDHT peer;
	public static final String LOCAL_HOST = "127.0.0.1";
	public static final int BASE_PORT = 4000;

	public BasePeer(int peerId) throws IOException {
		setPeer(new PeerBuilderDHT(new PeerBuilder(Number160.createHash(peerId)).ports(BASE_PORT + peerId).start())
				.start());
	}

	public PeerDHT getPeer() {
		return peer;
	}

	public void setPeer(PeerDHT peer) {
		this.peer = peer;
	}

	public byte[] get(String name) {
		FutureGet futureGet = getPeer().get(Number160.createHash(name)).start();
		futureGet.awaitUninterruptibly();
		if (futureGet.isSuccess()) {
			return futureGet.dataMap().values().iterator().next().toBytes();
		}
		return null;
	}

	public byte[] find(String name) throws InterruptedException {
		while (true) {
			FutureGet futureGet = getPeer().get(Number160.createHash(name)).start();
			futureGet.awaitUninterruptibly();
			if (futureGet.isSuccess() && !futureGet.dataMap().isEmpty()) {
				return futureGet.dataMap().values().iterator().next().toBytes();
			}
		}
	}

	public void store(String name, byte[] data) throws IOException {
		getPeer().put(Number160.createHash(name)).data(new Data(data)).start().awaitUninterruptibly();
	}

	public void shutdown() {
		getPeer().shutdown();
	}
}
