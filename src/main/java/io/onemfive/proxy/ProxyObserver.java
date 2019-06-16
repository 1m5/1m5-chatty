package io.onemfive.proxy;

import io.onemfive.proxy.packet.ProxyPacket;
import io.onemfive.proxy.packet.SendContentPacket;

/**
 * Register for updates from {@link ProxyPacket}s
 *
 * @since 0.6.1
 * @author objectorange
 */
public interface ProxyObserver {
    void messageReceived(SendContentPacket packet);
}
