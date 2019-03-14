package io.onemfive.proxy.packet;

import io.onemfive.data.content.Content;

/**
 * TODO: Add Description
 *
 * @author objectorange
 */
public class SendMessagePacket extends ProxyPacket {

    private Content message;

    public SendMessagePacket() {
        this(null, false);
    }

    public SendMessagePacket(boolean hashCashIt) {
        this(null, hashCashIt);
    }

    public SendMessagePacket(Content message, boolean hashCashIt) {
        super(hashCashIt);
        this.message = message;
    }

    public Content getMessage() {
        return message;
    }

    public void setMessage(Content message) {
        this.message = message;
    }
}
