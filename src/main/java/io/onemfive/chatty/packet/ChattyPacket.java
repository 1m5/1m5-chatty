package io.onemfive.chatty.packet;

import io.onemfive.core.ServiceRequest;
import io.onemfive.core.util.HashCash;

import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

/**
 * TODO: Add Description
 *
 * @author objectorange
 */
public abstract class ChattyPacket extends ServiceRequest {

    private static Logger LOG = Logger.getLogger(ChattyPacket.class.getName());

    private Long id;
    protected Map<String,Object> fromPeer;
    protected Map<String,Object> toPeer;
    protected String data;
    private long timeSent = 0;
    private long timeDelivered = 0;
    private long timeAcknowledged = 0;

    private HashCash hashCash;

    public ChattyPacket() {
        id = new Random(System.currentTimeMillis()).nextLong();
        try {
            hashCash = HashCash.mintCash(new Random().nextLong()+"",1);
        } catch (NoSuchAlgorithmException e) {
            LOG.warning(e.getLocalizedMessage());
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Map<String, Object> getFromPeer() {
        return fromPeer;
    }

    public void setFromPeer(Map<String, Object> fromPeer) {
        this.fromPeer = fromPeer;
    }

    public Map<String, Object> getToPeer() {
        return toPeer;
    }

    public void setToPeer(Map<String, Object> toPeer) {
        this.toPeer = toPeer;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
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
