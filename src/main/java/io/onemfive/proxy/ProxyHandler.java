package io.onemfive.proxy;

import io.onemfive.clearnet.server.EnvelopeProxyDataHandler;
import io.onemfive.data.Envelope;
import io.onemfive.data.util.DLC;
import io.onemfive.sensors.SensorsService;

import java.util.logging.Logger;

/**
 * Handles all requests from local browser.
 *
 * @since 0.6.1
 * @author objectorange
 */
public class ProxyHandler extends EnvelopeProxyDataHandler {

    private static Logger LOG = Logger.getLogger(ProxyHandler.class.getName());

    private static ProxyService proxyService;

    public ProxyHandler(){}

    public static void setProxyService(ProxyService client) {
        proxyService = client;
    }

    /**
     * Pack Envelope into appropriate inbound request and route to bus
     * @param e
     */
    @Override
    protected void route(Envelope e) {
        LOG.info("Changing Sensitivity to Medium to pick up Tor Sensor...");
        // Change flag to Medium so Tor Sensor will pick up
        e.setSensitivity(Envelope.Sensitivity.MEDIUM);
        sensor.send(e);
    }

}
