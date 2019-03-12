package io.onemfive.proxy;

import io.onemfive.clearnet.server.EnvelopeJSONDataHandler;
import io.onemfive.clearnet.server.Session;
import io.onemfive.core.keyring.AuthNRequest;
import io.onemfive.core.keyring.GenerateKeyRingCollectionsRequest;
import io.onemfive.core.keyring.KeyRingService;
import io.onemfive.data.DID;
import io.onemfive.data.Envelope;
import io.onemfive.data.util.DLC;
import io.onemfive.data.util.JSONParser;
import io.onemfive.did.AuthenticateDIDRequest;
import io.onemfive.did.DIDService;
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
            case "identify": {
                LOG.info("Identify request..");
                String username = params.get("u");
                String passphrase = params.get("p");
                String passphrase2 = params.get("p2");
                DID did = new DID();
                did.setUsername(username);
                did.setPassphrase(passphrase);
                if(passphrase2 != null) {
                    // 2. Persist new DID
                    did.setAuthenticated(true);
                    DLC.addData(DID.class, did,e);
                    DLC.addRoute(DIDService.class, DIDService.OPERATION_SAVE,e);

                    // 1. Create and save KeyRingCollection
                    GenerateKeyRingCollectionsRequest gkr = new GenerateKeyRingCollectionsRequest();
                    gkr.keyRingUsername = username;
                    gkr.keyRingPassphrase = passphrase;
                    DLC.addData(GenerateKeyRingCollectionsRequest.class, gkr,e);
                    DLC.addRoute(KeyRingService.class, KeyRingService.OPERATION_GENERATE_KEY_RINGS_COLLECTIONS,e);
                } else {
                    // 2. Load DID
                    AuthenticateDIDRequest r = new AuthenticateDIDRequest();
                    r.did = did;

                    DLC.addData(AuthenticateDIDRequest.class,r,e);
                    DLC.addRoute(DIDService.class, DIDService.OPERATION_AUTHENTICATE,e);

                    // 1. Load Public Key addresses for short and full addresses
                    AuthNRequest ar = new AuthNRequest();
                    ar.keyRingUsername = username;
                    ar.keyRingPassphrase = passphrase;
                    ar.alias = username;
                    ar.aliasPassphrase = passphrase;
                    DLC.addData(AuthNRequest.class,ar,e);
                    DLC.addRoute(KeyRingService.class, KeyRingService.OPERATION_AUTHN,e);
                }
                break;
            }
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
