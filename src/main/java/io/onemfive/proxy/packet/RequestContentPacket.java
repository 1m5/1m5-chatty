package io.onemfive.proxy.packet;

import io.onemfive.data.content.Content;
import io.onemfive.proxy.ProxyService;

import java.net.URL;

/**
 * {@link ProxyPacket} for requesting {@link Content}
 * from a {@link java.net.URL} using
 * a {@link ProxyService}
 *
 * @author objectorange
 */
public class RequestContentPacket extends ProxyPacket {

    private URL url;
    private Content content;

    public RequestContentPacket() {
        super(false);
    }

    public RequestContentPacket(boolean hashCashIt) {
        super(hashCashIt);
    }

    public RequestContentPacket(URL url) {
        super(false);
        this.url = url;
    }

    public RequestContentPacket(URL url, boolean hashCashIt) {
        super(hashCashIt);
        this.url = url;
    }

    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    public Content getContent() {
        return content;
    }

    public void setContent(Content content) {
        this.content = content;
    }
}
