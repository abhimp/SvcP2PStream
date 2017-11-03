package com.mtp.p2p;

import java.io.IOException;
import java.net.InetAddress;

import net.tomp2p.futures.FutureBootstrap;

public class ClientPeer extends BasePeer {

	public ClientPeer(int peerId) throws IOException {
		super(peerId);

		FutureBootstrap fb = this.getPeer().peer().bootstrap().inetAddress(InetAddress.getByName(LOCAL_HOST))
				.ports(BASE_PORT).start();
		fb.awaitUninterruptibly();
		if (fb.isSuccess()) {
			getPeer().peer().discover().peerAddress(fb.bootstrapTo().iterator().next()).start().awaitUninterruptibly();
		}
	}
}
