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
public abstract class ProxyPacket extends ServiceRequest {

    private static Logger LOG = Logger.getLogger(ProxyPacket.class.getName());

    protected Long id;
    protected NetworkPeer fromPeer;
    protected NetworkPeer toPeer;
    protected byte[] data;
    protected long timeSent = 0;
    protected long timeDelivered = 0;
    protected long timeAcknowledged = 0;

    private HashCash hashCash;

    public ProxyPacket() {
        this(false);
    }

    public ProxyPacket(boolean hashCashIt) {
        id = new Random(System.currentTimeMillis()).nextLong();
        if(hashCashIt) {
            mintHashCash();
        }
    }

    public boolean mintHashCash() {
        try {
            hashCash = HashCash.mintCash(id + "", 1);
        } catch (NoSuchAlgorithmException e) {
            LOG.warning(e.getLocalizedMessage());
            return false;
        }
        return true;
    }

    public boolean verifyHashCash(HashCash hashCash) {
        // TODO: needs implemented
        return true;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public long getTimeSent() {
        return timeSent;
    }

    public void setTimeSent(long timeSent) {
        this.timeSent = timeSent;
    }

    public long getTimeDelivered() {
        return timeDelivered;
    }

    public void setTimeDelivered(long timeDelivered) {
        this.timeDelivered = timeDelivered;
    }

    public long getTimeAcknowledged() {
        return timeAcknowledged;
    }

    public void setTimeAcknowledged(long timeAcknowledged) {
        this.timeAcknowledged = timeAcknowledged;
    }
}
