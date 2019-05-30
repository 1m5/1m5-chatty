package io.onemfive.proxy.packet;

import io.onemfive.core.ServiceRequest;
import io.onemfive.core.util.HashCash;
import io.onemfive.data.NetworkPeer;

import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Base packet for all communications between {@link io.onemfive.proxy.ProxyClient}
 * (producer) and {@link io.onemfive.proxy.ProxyHandler} (consumer).
 *
 * @since 0.6.1
 * @author objectorange
 */
public abstract class P2PProxyPacket extends ProxyPacket {

    private static Logger LOG = Logger.getLogger(P2PProxyPacket.class.getName());

    protected NetworkPeer fromPeer;
    protected NetworkPeer toPeer;

    public P2PProxyPacket() {
        super(false);
    }

    public P2PProxyPacket(NetworkPeer from, NetworkPeer to) {
        fromPeer = from;
        toPeer = to;
    }

    public P2PProxyPacket(boolean hashCashIt) {
        super(hashCashIt);
    }

    public P2PProxyPacket(NetworkPeer from, NetworkPeer to, boolean hashCashIt) {
        super(hashCashIt);
        fromPeer = from;
        toPeer = to;
    }

    public NetworkPeer getFromPeer() {
        return fromPeer;
    }

    public void setFromPeer(NetworkPeer fromPeer) {
        this.fromPeer = fromPeer;
    }

    public NetworkPeer getToPeer() {
        return toPeer;
    }

    public void setToPeer(NetworkPeer toPeer) {
        this.toPeer = toPeer;
    }

}
