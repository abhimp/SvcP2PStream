package com.mtp.p2p;

import java.io.IOException;
import java.net.InetAddress;

import net.tomp2p.futures.FutureBootstrap;

public class ClientPeer extends BasePeer {

	public ClientPeer(int peerId, String serverAddress, int serverPort) throws IOException {
		super(peerId);

		FutureBootstrap fb = this.getPeer().peer().bootstrap().inetAddress(InetAddress.getByName(serverAddress))
				.ports(serverPort).start();
		fb.awaitUninterruptibly();
		if (fb.isSuccess()) {
			getPeer().peer().discover().peerAddress(fb.bootstrapTo().iterator().next()).start().awaitUninterruptibly();
		}
	}
}
