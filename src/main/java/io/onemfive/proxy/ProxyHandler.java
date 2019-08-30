package io.onemfive.proxy;

import io.onemfive.clearnet.server.EnvelopeProxyDataHandler;
import io.onemfive.data.Envelope;
import io.onemfive.data.util.DLC;
import io.onemfive.sensors.SensorsService;
import org.eclipse.jetty.server.Request;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
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

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        Envelope envelope = this.parseEnvelope(request);
        EnvelopeProxyDataHandler.ClientHold clientHold = new EnvelopeProxyDataHandler.ClientHold(target, baseRequest, request, response, envelope);
        requests.put(envelope.getId(), clientHold);
        DLC.addRoute(SensorsService.class, "SEND", envelope);
        route(envelope);
        if (DLC.getErrorMessages(envelope).size() > 0) {
            LOG.warning("Returning HTTP 500...");
            response.setStatus(500);
            baseRequest.setHandled(true);
            requests.remove(envelope.getId());
        } else {
            clientHold.hold(600000L);
        }
    }

    /**
     * Pack Envelope into appropriate inbound request and route to bus
     * @param e
     */
    @Override
    protected void route(Envelope e) {
        LOG.info("Received Envelope in Proxy Handler...");
        // Change flag to Medium so Tor Sensor will pick up
        e.setSensitivity(Envelope.Sensitivity.MEDIUM);
        sensor.send(e);
    }

}
