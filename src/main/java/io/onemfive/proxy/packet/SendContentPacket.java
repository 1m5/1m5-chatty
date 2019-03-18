package io.onemfive.proxy.packet;

import io.onemfive.data.content.Content;

/**
 * {@link ProxyPacket} for sending {@link Content}
 * to a {@link io.onemfive.data.NetworkPeer} using
 * a {@link io.onemfive.proxy.ProxyClient}
 *
 * @author objectorange
 */
public class SendContentPacket extends ProxyPacket {

    private Content content;

    public SendContentPacket() {
        this(null, false);
    }

    public SendContentPacket(boolean hashCashIt) {
        this(null, hashCashIt);
    }

    public SendContentPacket(Content content, boolean hashCashIt) {
        super(hashCashIt);
        this.content = content;
    }

    public Content getContent() {
        return content;
    }

    public void setContent(Content content) {
        this.content = content;
    }
}
