package io.onemfive.proxy;

import io.onemfive.clearnet.server.ClearnetServerSensor;
import io.onemfive.core.*;
import io.onemfive.core.admin.AdminService;
import io.onemfive.core.client.Client;
import io.onemfive.core.client.ClientAppManager;
import io.onemfive.core.client.ClientStatusListener;
import io.onemfive.core.keyring.AuthNRequest;
import io.onemfive.core.keyring.KeyRingService;
import io.onemfive.core.notification.NotificationService;
import io.onemfive.core.notification.SubscriptionRequest;
import io.onemfive.data.*;
import io.onemfive.data.content.Content;
import io.onemfive.data.content.Text;
import io.onemfive.data.util.DLC;
import io.onemfive.data.util.FileUtil;
import io.onemfive.data.util.HashUtil;
import io.onemfive.data.util.JSONParser;
import io.onemfive.did.AuthenticateDIDRequest;
import io.onemfive.did.DIDService;
import io.onemfive.i2p.I2PSensor;
import io.onemfive.proxy.packet.ProxyPacket;
import io.onemfive.proxy.packet.SendContentPacket;
import io.onemfive.sensors.Sensor;
import io.onemfive.sensors.SensorManager;
import io.onemfive.sensors.SensorRequest;
import io.onemfive.sensors.SensorsService;
import io.onemfive.tor.client.TorClientSensor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Encapsulates all client interactions for {@link ProxyDaemon}
 *
 * @since 0.6.1
 * @author objectorange
 */
public class ProxyService extends BaseService {

    private Logger LOG = Logger.getLogger(ProxyService.class.getName());

    protected Properties config;
    protected List<ProxyObserver> observers = new ArrayList<>();

    protected Client client;
    protected NetworkPeer localPeer;

    public void routeIn(Envelope envelope) {
        LOG.info("Route In from Notification Service...");
        DID fromDid = envelope.getDID();
        LOG.info("From DID pulled from Envelope.");
        NetworkPeer fromPeer = new NetworkPeer(NetworkPeer.Network.IMS.name());
        fromPeer.setDid(fromDid);
        EventMessage m = (EventMessage)envelope.getMessage();
        Object msg = m.getMessage();
        Object obj;
        if(msg instanceof String) {
            // Raw
            Map<String, Object> mp = (Map<String, Object>) JSONParser.parse(msg);
            String type = (String) mp.get("type");
            if (type == null) {
                LOG.warning("Attribute 'type' not found in EventMessage message. Unable to instantiate object.");
                return;
            }
            try {
                obj = Class.forName(type).newInstance();
            } catch (InstantiationException e) {
                LOG.warning("Unable to instantiate class: " + type);
                return;
            } catch (IllegalAccessException e) {
                LOG.severe(e.getLocalizedMessage());
                return;
            } catch (ClassNotFoundException e) {
                LOG.warning("Class not on classpath: " + type);
                return;
            }
            if (obj instanceof ProxyPacket) {
                LOG.info("Object a ProxyPacket...");
                ProxyPacket packet = (ProxyPacket) obj;
                packet.fromMap(mp);
                packet.setTimeDelivered(System.currentTimeMillis());
                switch (type) {
                    case "io.onemfive.proxy.packet.SendContentPacket": {
                        sendContentIn((SendContentPacket) packet);
                        break;
                    }
                    default:
                        LOG.warning(obj+" not yet supported. ");
                }
            } else {
                LOG.warning("Object " + obj.getClass().getName() + " not handled.");
            }
        } else if(msg instanceof DID) {
            LOG.info("Route in DID...");
            localPeer = new NetworkPeer(NetworkPeer.Network.IMS.name());
            localPeer.setDid((DID)msg);
        } else {
            LOG.warning("EnvelopeMessage message "+msg.getClass().getName()+" not handled.");
        }
    }

    private void sendContentIn(SendContentPacket packet) {
        LOG.info("Received SendContentPacket...");
        // TODO: Persist Content
        Content c = packet.getContent();
        if(c instanceof Text) {
            Text t = (Text)c;
            LOG.info("Received text from peer: "+new String(t.getBody()));
            for(ProxyObserver o : observers) {
                o.messageReceived(packet);
            }
        } else {
            LOG.warning("Content type not currently supported: "+c.getClass().getName());
        }
    }

    private void sendContentOut(SendContentPacket packet) {
        LOG.info("Sending Content out...");
        Envelope e = Envelope.documentFactory();
        e.setSensitivity(Envelope.Sensitivity.HIGH); // Flag for I2P
        SensorRequest r = new SensorRequest();
        r.from = packet.getFromPeer().getDid();
        r.to = packet.getToPeer().getDid();
        r.content = new String(packet.getContent().getBody());
        client.request(e);
    }

    @Override
    public boolean start(Properties properties) {
        if(!super.start(properties)) {
            return false;
        }
        LOG.info("Starting ProxyService...");
        updateStatus(ServiceStatus.STARTING);
        try {
            config = Config.loadFromClasspath("1m5-proxy.config", properties, false);
        } catch (Exception e) {
            LOG.warning(e.getLocalizedMessage());
        }
        // Ensure Service Directory Path
        if(config.getProperty("1m5.proxy.dir.base")==null) {
            String serviceDirPath;
            try {
                serviceDirPath = getServiceDirectory().getCanonicalPath();
                File sPath = new File(serviceDirPath);
                if(!sPath.exists() && !sPath.mkdir()) {
                    LOG.warning("Unable to create service path for 1M5 Proxy Service. Start failed.");
                    return false;
                }
                config.put("1m5.proxy.dir.base", serviceDirPath);
            } catch (IOException e) {
                LOG.warning(e.getLocalizedMessage());
                return false;
            }
        }
        // Credentials
        String username = "Alice235";
        String passphrase = null;
        try {
            String credFileStr = getServiceDirectory().getAbsolutePath() + "/cred";
            File credFile = new File(credFileStr);
            if (!credFile.exists())
                if (!credFile.createNewFile())
                    throw new Exception("Unable to create node credentials file at: " + credFileStr);

            config.setProperty("username", "Alice235");
            passphrase = FileUtil.readTextFile(credFileStr, 1, true);
            if ("".equals(passphrase) ||
                    (config.getProperty("1m5.node.local.rebuild") != null && "true".equals(config.getProperty("1m5.node.local.rebuild")))) {
                passphrase = HashUtil.generateHash(String.valueOf(System.currentTimeMillis()), Hash.Algorithm.SHA1).getHash();
                if (!FileUtil.writeFile(passphrase.getBytes(), credFileStr))
                    throw new Exception("Unable to write local node Alice235 passphrase to file.");
                else
                    LOG.info("New passphrase saved: " + passphrase);
            }
            config.setProperty("1m5.node.local.passphrase", passphrase);
        } catch (Exception e) {
            LOG.warning(e.getLocalizedMessage());
            return false;
        }

        Subscription subscription = new Subscription() {
            @Override
            public void notifyOfEvent(Envelope e) {
                routeIn(e);
            }
        };

        // Subscribe to Text notifications
        SubscriptionRequest r = new SubscriptionRequest(EventMessage.Type.TEXT, subscription);
        Envelope e2 = Envelope.documentFactory();
        DLC.addData(SubscriptionRequest.class, r, e2);
        DLC.addRoute(NotificationService.class, NotificationService.OPERATION_SUBSCRIBE, e2);
        client.request(e2);

        // Subscribe to DID status notifications
        SubscriptionRequest r2 = new SubscriptionRequest(EventMessage.Type.STATUS_DID, subscription);
        Envelope e3 = Envelope.documentFactory();
        DLC.addData(SubscriptionRequest.class, r2, e3);
        DLC.addRoute(NotificationService.class, NotificationService.OPERATION_SUBSCRIBE, e3);
        client.request(e3);

        Envelope e4 = Envelope.documentFactory();

        // 2. Authenticate DID
        DID did = new DID();
        did.setUsername(username);
        did.setPassphrase(passphrase);
        AuthenticateDIDRequest adr = new AuthenticateDIDRequest();
        adr.did = did;
        adr.autogenerate = true;
        DLC.addData(AuthenticateDIDRequest.class, adr, e4);
        DLC.addRoute(DIDService.class, DIDService.OPERATION_AUTHENTICATE, e4);

        // 1. Load Public Key addresses for short and full addresses
        AuthNRequest ar = new AuthNRequest();
        ar.keyRingUsername = username;
        ar.keyRingPassphrase = passphrase;
        ar.alias = username; // use username as default alias
        ar.aliasPassphrase = passphrase; // just use same passphrase
        ar.autoGenerate = true;
        DLC.addData(AuthNRequest.class, ar, e4);
        DLC.addRoute(KeyRingService.class, KeyRingService.OPERATION_AUTHN, e4);
        client.request(e4);

        updateStatus(ServiceStatus.RUNNING);
        LOG.info("1M5 Proxy Started.");

        return true;
    }

    @Override
    public boolean pause() {
        return false;
    }

    @Override
    public boolean unpause() {
        return false;
    }

    @Override
    public boolean restart() {
        return false;
    }

    @Override
    public boolean shutdown() {
        return false;
    }

    @Override
    public boolean gracefulShutdown() {
        return false;
    }

    protected void waitABit(long waitTime) {
        try {
            Thread.sleep(waitTime);
        } catch (InterruptedException e) {}
    }
}
