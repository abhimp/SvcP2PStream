package com.mtp.p2p;

import java.io.IOException;
import java.net.InetAddress;

import net.tomp2p.futures.FutureBootstrap;

public class ServerPeer extends BasePeer {

	public ServerPeer() throws IOException {
		super(0);

		FutureBootstrap fb = this.getPeer().peer().bootstrap().inetAddress(InetAddress.getByName(LOCAL_HOST))
				.ports(BASE_PORT).start();
		fb.awaitUninterruptibly();
		if (fb.isSuccess()) {
			getPeer().peer().discover().peerAddress(fb.bootstrapTo().iterator().next()).start().awaitUninterruptibly();
		}
	}

}
