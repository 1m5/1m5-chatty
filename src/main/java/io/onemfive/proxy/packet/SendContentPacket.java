package io.onemfive.proxy.packet;

import io.onemfive.data.NetworkPeer;
import io.onemfive.data.content.Content;
import io.onemfive.proxy.ProxyService;

/**
 * {@link P2PProxyPacket} for sending {@link Content}
 * to a {@link io.onemfive.data.NetworkPeer} using
 * a {@link ProxyService}
 *
 * @author objectorange
 */
public class SendContentPacket extends P2PProxyPacket {

    private Content content;

    public SendContentPacket() {
        this(null, false);
    }

    public SendContentPacket(Content content, boolean hashCashIt) {
        super(hashCashIt);
        this.content = content;
    }

    public SendContentPacket(NetworkPeer from, NetworkPeer to, Content content) {
        super(from, to, false);
        this.content = content;
    }

    public SendContentPacket(NetworkPeer from, NetworkPeer to, Content content, boolean hashCashIt) {
        super(from, to, hashCashIt);
        this.content = content;
    }

    public Content getContent() {
        return content;
    }

    public void setContent(Content content) {
        this.content = content;
    }
}
