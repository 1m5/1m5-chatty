package io.onemfive.proxy;

import io.onemfive.clearnet.server.EnvelopeJSONDataHandler;
import io.onemfive.clearnet.server.Session;
import io.onemfive.data.Envelope;
import io.onemfive.data.util.DLC;
import io.onemfive.data.util.JSONParser;
import io.onemfive.sensors.SensorRequest;

import java.util.*;
import java.util.logging.Logger;

/**
 * TODO: Add Description
 *
 * @author objectorange
 */
public class APIHandler extends EnvelopeJSONDataHandler {

    private static Logger LOG = Logger.getLogger(APIHandler.class.getName());

    public APIHandler(){}

    /**
     * Pack Envelope into appropriate inbound request and route to bus
     * @param e
     */
    @Override
    protected void route(Envelope e) {
        String command = e.getCommandPath();
        String sessionId = (String)e.getHeader(Session.class.getName());
        Session session = activeSessions.get(sessionId);
        Map<String,String> params = (Map<String,String>)DLC.getData(Map.class, e);
        switch(command) {
            case "send": {
                LOG.info("Send request..");
                String msg = params.get("m");
                e.setSensitivity(Envelope.Sensitivity.HIGH); // Flag for I2P
                SensorRequest r = new SensorRequest();

                sensor.send(e);
                break;
            }
            case "stats": {
                LOG.info("Stats request...");

//                sensor.send(e);
                break;
            }
            default: {
                LOG.info("Unhandled request..");
                String err = "Ignoring request. Command not handled: "+command;
                LOG.warning(err);
                DLC.addErrorMessage(err, e);
            }
        }
    }

    @Override
    protected String unpackEnvelopeContent(Envelope e) {
        LOG.info("Unpacking Envelope...");
        Map<String,Object> m = new HashMap<>();
        String command = e.getCommandPath();
        m.put("command",command);
        switch(command) {
            case "send": {
                LOG.info("Send message acknowledged.");
                break;
            }
            case "stats": {
                LOG.info("Stats response...");

                break;
            }
            default: {
                LOG.info("Unhandled response..");
                String err = "Ignoring response. Command not handled: "+command;
                LOG.warning(err);
                DLC.addErrorMessage(err, e);
            }

        }
        return JSONParser.toString(m);
    }
}
