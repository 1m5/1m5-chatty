package io.onemfive.proxy;

import io.onemfive.clearnet.server.ClearnetServerSensor;
import io.onemfive.clearnet.server.EnvelopeWebSocket;
import io.onemfive.data.Envelope;
import io.onemfive.data.EventMessage;
import io.onemfive.data.util.DLC;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Push event content to browser.
 *
 * @author objectorange
 */
public class EventWebSocket extends EnvelopeWebSocket {

    private Logger LOG = Logger.getLogger(EventWebSocket.class.getName());

    public EventWebSocket() {
        super();
    }

    public EventWebSocket(ClearnetServerSensor sensor) {
        super(sensor);
    }

    public void pushEnvelope(Envelope e) {
        EventMessage em = DLC.getEventMessage(e);
        Object msg = em.getMessage();
        if(session==null) {
            LOG.warning("Jetty WebSocket session not yet established. Unable to send message.");
            return;
        }
        if(msg instanceof String) {
            String txt = (String)msg;
            LOG.finer("Received text from Notification Service: "+txt);
            if(txt.contains("io.onemfive.proxy.packet.RequestContentPacket")) {
                LOG.info("Text contains RequestContentPacket.");
                sendMessage(txt);
            }
        } else {
            LOG.info("EventWebSocket only processes text.");
        }
    }

    private void sendMessage(String text) {
        RemoteEndpoint endpoint = session.getRemote();
        if(endpoint==null) {
            LOG.warning("No RemoteEndpoint found for current Jetty WebSocket session.");
        } else {
            try {
                LOG.info("Sending result to browser...");
                endpoint.sendString(text);
                LOG.info("Text result to browser.");
            } catch (IOException e1) {
                LOG.warning("IOException caught attempting to send text to browser: "+e1.getLocalizedMessage());
            }
        }
    }
}
