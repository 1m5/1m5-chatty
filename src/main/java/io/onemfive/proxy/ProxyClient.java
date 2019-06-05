package io.onemfive.proxy;

import io.onemfive.clearnet.server.ClearnetServerSensor;
import io.onemfive.core.LifeCycle;
import io.onemfive.core.OneMFiveAppContext;
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
import io.onemfive.data.util.JSONParser;
import io.onemfive.did.AuthenticateDIDRequest;
import io.onemfive.did.DIDService;
import io.onemfive.i2p.I2PSensor;
import io.onemfive.proxy.packet.ProxyPacket;
import io.onemfive.proxy.packet.SendContentPacket;
import io.onemfive.sensormanager.graph.SensorManagerGraph;
import io.onemfive.sensors.Sensor;
import io.onemfive.sensors.SensorManager;
import io.onemfive.sensors.SensorRequest;
import io.onemfive.sensors.SensorsService;
import io.onemfive.tor.client.TorClientSensor;

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
public class ProxyClient implements LifeCycle {

    private Logger LOG = Logger.getLogger(ProxyClient.class.getName());

    protected Properties config;
    protected List<ProxyObserver> observers;

    ClientAppManager manager;
    protected ClientAppManager.Status clientAppManagerStatus;
    protected Client client;
    protected NetworkPeer localPeer;
    protected SensorManagerGraph graph;

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
        config = properties;
        observers = new ArrayList<>();
        // Getting ClientAppManager starts 1M5 Bus
        OneMFiveAppContext oneMFiveAppContext = OneMFiveAppContext.getInstance(config);
        manager = oneMFiveAppContext.getClientAppManager();
        manager.setShutdownOnLastUnregister(true);
        client = manager.getClient(true);

        ClientStatusListener clientStatusListener = new ClientStatusListener() {
            @Override
            public void clientStatusChanged(ClientAppManager.Status clientStatus) {
                clientAppManagerStatus = clientStatus;
                LOG.info("Network Status changed: "+clientStatus.name());
                switch(clientAppManagerStatus) {
                    case INITIALIZING: {
                        LOG.info("Network initializing...");
                        break;
                    }
                    case READY: {
                        LOG.info("Network ready.");
                        break;
                    }
                    case STOPPING: {
                        LOG.info("Network stopping...");
                        break;
                    }
                    case STOPPED: {
                        LOG.info("Network stopped.");
                        break;
                    }
                }
            }
        };
        client.registerClientStatusListener(clientStatusListener);
        final Thread mainThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                manager.unregister(client);
                try {
                    mainThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        // wait a second to let the bus and internal services start
        waitABit(1000);

        // Initialize and configure 1M5
        Envelope e = Envelope.documentFactory();

        // Setup Services
        List<Class> services = new ArrayList<>();
        services.add(DIDService.class);
        services.add(SensorsService.class);

        // Setup SensorManagerNeo4j
        config.setProperty(SensorManager.class.getName(),SensorManagerGraph.class.getName());

        // Setup Sensors
        // I2P
        String sensorsConfig = I2PSensor.class.getName()+","+Envelope.Sensitivity.HIGH.name()+",100";
        // Tor
        sensorsConfig += ":"+ TorClientSensor.class.getName()+","+Envelope.Sensitivity.MEDIUM.name()+",100";
        config.setProperty(Sensor.class.getName(), sensorsConfig);

        DLC.addEntity(services, e);
        DLC.addData(Properties.class, config, e);

        // Register Services
        DLC.addRoute(AdminService.class, AdminService.OPERATION_REGISTER_SERVICES,e);
        client.request(e);

        // Wait a bit for Sensors and DID to start
        waitABit(1000);

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
        String username = config.getProperty("username");
        String passphrase = config.getProperty("passphrase");
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
