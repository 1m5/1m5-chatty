package io.onemfive.proxy;

import io.onemfive.clearnet.server.ClearnetServerSensor;
import io.onemfive.core.Config;
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
import io.onemfive.data.util.DLC;
import io.onemfive.data.util.JSONParser;
import io.onemfive.did.AuthenticateDIDRequest;
import io.onemfive.did.DIDService;
import io.onemfive.i2p.I2PSensor;
import io.onemfive.proxy.packet.ProxyPacket;
import io.onemfive.proxy.packet.SendMessagePacket;
import io.onemfive.sensormanager.neo4j.SensorManagerNeo4j;
import io.onemfive.sensors.Sensor;
import io.onemfive.sensors.SensorManager;
import io.onemfive.sensors.SensorRequest;
import io.onemfive.sensors.SensorsService;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class ProxyDaemon {

    private static final Logger LOG = Logger.getLogger(ProxyDaemon.class.getName());

    private enum Status {Shutdown, Initializing, Initialized, Starting, Running, ShuttingDown, Errored, Exiting}

    protected static Properties config;
    protected static ProxyDaemon proxyDaemon;

    protected ClientAppManager manager;
    protected ClientAppManager.Status clientAppManagerStatus;
    protected Client client;
    protected boolean running = false;
    protected Scanner scanner;
    protected Status status = Status.Shutdown;

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
                    case "io.onemfive.pureos.packet.SendMessagePacket": {
                        sendMessageIn((SendMessagePacket) packet);
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
            updateI2PAddress((DID)msg);
        } else {
            LOG.warning("EnvelopeMessage message "+msg.getClass().getName()+" not handled.");
        }
    }

    private void updateI2PAddress(DID did) {
        localPeer = new NetworkPeer(NetworkPeer.Network.IMS.name());
        localPeer.setDid(did);
    }

    private void sendMessageIn(SendMessagePacket packet) {
        LOG.info("Received SendMessagePacket...");

    }

    private void sendMessageOut(SendMessagePacket packet) {
        LOG.info("Sending Message out...");
        Envelope e = Envelope.documentFactory();
        e.setSensitivity(Envelope.Sensitivity.HIGH); // Flag for I2P
        SensorRequest r = new SensorRequest();
        r.content = new String(packet.getMessage().getBody());
        client.request(e);
    }

    public static void main(String[] args) {
        System.out.println("Welcome to 1M5 Proxy Daemon. Starting 1M5 Service...");
        Properties p = new Properties();
        String[] parts;
        for(String arg : args) {
            parts = arg.split("=");
            p.setProperty(parts[0],parts[1]);
        }

        loadLoggingProperties(p);

        try {
            config = Config.loadFromClasspath("1m5-proxy.config", p, false);
        } catch (Exception e) {
            System.out.println(e.getLocalizedMessage());
            System.exit(-1);
        }

//        String useConsole = config.getProperty("console");
//        if("true".equals(useConsole)) {
//            scanner = new Scanner(System.in);
//            running = true;
//            while(status != Status.Exiting) {
//                printMenu();
//                processCommand(scanner.nextLine());
//            }
//            scanner.close();
//        } else {
            try {
                proxyDaemon = new ProxyDaemon();
                proxyDaemon.launch();
                // Check periodically to see if 1M5 stopped
                while (proxyDaemon.clientAppManagerStatus != ClientAppManager.Status.STOPPED) {
                    proxyDaemon.waitABit(2 * 1000);
                }
            } catch (Exception e) {
                e.printStackTrace();
                LOG.severe(e.getLocalizedMessage());
                System.exit(-1);
            }
//        }

        System.out.println("1M5 Proxy Daemon exiting...");
        System.exit(0);
    }

    protected void launch() throws Exception {

        // Directories
        String rootDir = config.getProperty("1m5.dir.root");
        if(rootDir == null) {
            rootDir = System.getProperty("user.dir");
            config.setProperty("1m5.dir.root",rootDir);
        }

        String oneMFiveDir = rootDir + "/.1m5";
        File oneMFiveFolder = new File(oneMFiveDir);
        if(!oneMFiveFolder.exists())
            if(!oneMFiveFolder.mkdir())
                throw new Exception("Unable to create 1M5 base directory: "+oneMFiveDir);
        config.setProperty("1m5.dir.base",oneMFiveDir);
        LOG.config("1M5 Root Directory: "+oneMFiveDir);

        String username = config.getProperty("username");
        if(username==null) {
            LOG.severe("node username required.");
            return;
        }
        String passphrase = config.getProperty("passphrase");
        if(passphrase == null) {
            LOG.severe("node passphrase required.");
            return;
        }

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
        config.setProperty(SensorManager.class.getName(),SensorManagerNeo4j.class.getName());

        // Setup Sensors
        String sensorsConfig = I2PSensor.class.getName()+","+Envelope.Sensitivity.HIGH.name()+",100";
        if("true".equals(config.getProperty("onemfive.sensors.clearnet.server"))) {
            sensorsConfig += ":"+ClearnetServerSensor.class.getName()+","+Envelope.Sensitivity.LOW.name()+",100";
        }
        config.setProperty(Sensor.class.getName(), sensorsConfig);

        DLC.addEntity(services, e);
        DLC.addData(Properties.class, config, e);

        // Register Services
        DLC.addRoute(AdminService.class, AdminService.OPERATION_REGISTER_SERVICES,e);
        client.request(e);

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
    }

    protected void waitABit(long waitTime) {
        try {
            Thread.sleep(waitTime);
        } catch (InterruptedException e) {}
    }

    private void printMenu() {
        System.out.println("The following commands are available: ");
        switch(status) {
            case Shutdown: {
                System.out.println("\tin - Initializing the 1M5 Proxy Daemon.");
                System.out.println("\tq  - Quit.");
                break;
            }
            case Initialized: {
                System.out.println("\tst - Start the 1M5 Proxy Daemon.");
                System.out.println("\tc  - Print Config.");
                System.out.println("\tq  - Quit.");
                break;
            }
            case Running: {
                System.out.println("\tsd - Shutdown the 1M5 Proxy Daemon.");
                System.out.println("\tc  - Print Config.");
            }
        }
    }

    private void processCommand(String command) {
        switch (command) {
            case "c" : {
                if(status == Status.Initialized || status == Status.Running) {
                    printConfig();
                } else {
                    System.out.println("Command not available.");
                    printMenu();
                    return;
                }
                break;
            }
            case "q" : {
                System.out.println("Exiting 1M5 Proxy Daemon....");
                status = Status.Exiting;
                System.exit(0);
                break;
            }
            case "in": {
                if(status != Status.Shutdown) {
                    System.out.println("Command not available.");
                    printMenu();
                    return;
                }
                System.out.println("Initializing 1M5 Proxy Daemon....");
                status = Status.Initializing;
                try {
                    initialize();
                    status = Status.Initialized;
                    System.out.println("1M5 Proxy Daemon Initialized.");
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("1M5 Proxy Daemon failed to Initialize.");
                }
                break;
            }
            case "st": {
                if(status != Status.Initialized) {
                    System.out.println("Command not available.");
                    printMenu();
                    return;
                }
                System.out.println("Starting 1M5 Proxy Daemon....");
                status = Status.Starting;
                String username = null;
                while(username==null) {
                    System.out.println("Please enter a username");
                    username = scanner.nextLine();
                }
                String passphrase = null;
                while(passphrase == null) {
                    System.out.println("Please enter a passphrase");
                    passphrase = scanner.nextLine();
                }
                startService(username, passphrase);
                status = Status.Running;
                System.out.println("1M5 Proxy Daemon Running.");
                break;
            }
            case "sd": {
                if(status != Status.Running) {
                    System.out.println("Command not available.");
                    printMenu();
                    return;
                }
                System.out.println("Shutting Down 1M5 Proxy Daemon....");
                status = Status.ShuttingDown;
                stopService();
                status = Status.Shutdown;
                System.out.println("1M5 Proxy Daemon Shutdown.");
                break;
            }
        }
    }

    private void initialize() throws Exception {
        // Directories
        String rootDir = config.getProperty("1m5-proxy.dir.base");
        if(rootDir == null) {
            rootDir = System.getProperty("user.dir") + "/chat";
            config.setProperty("1m5-proxy.dir.base",rootDir);
        }
        File ikFolder = new File(rootDir);
        if(!ikFolder.exists())
            if(!ikFolder.mkdir())
                throw new Exception("Unable to create 1M5 Proxy directory: "+rootDir);
        System.out.println("1M5 Proxy Root Directory: "+rootDir);

        String oneMFiveDir = rootDir + "/.1m5";
        File oneMFiveFolder = new File(oneMFiveDir);
        if(!oneMFiveFolder.exists())
            if(!oneMFiveFolder.mkdir())
                throw new Exception("Unable to create 1M5 base directory: "+oneMFiveDir);
        config.setProperty("1m5.dir.base",oneMFiveDir);
        System.out.println("1M5 Root Directory: "+oneMFiveDir);
    }

    private void startService(String nodeUsername, String nodePassphrase) {
        // Start DCDN Service
        config.setProperty("username", nodeUsername);
        config.setProperty("passphrase", nodePassphrase);

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
        config.setProperty(SensorManager.class.getName(),SensorManagerNeo4j.class.getName());

        // Setup Sensors
        String sensorsConfig = I2PSensor.class.getName()+","+Envelope.Sensitivity.HIGH.name()+",100";
        if("true".equals(config.getProperty("1m5.sensors.clearnet.server"))) {
            sensorsConfig += ":"+ClearnetServerSensor.class.getName()+","+Envelope.Sensitivity.LOW.name()+",100";
        }
        config.setProperty(Sensor.class.getName(), sensorsConfig);

        DLC.addEntity(services, e);
        DLC.addData(Properties.class, config, e);

        // Register Services
        DLC.addRoute(AdminService.class, AdminService.OPERATION_REGISTER_SERVICES,e);
        client.request(e);
    }

    private static void stopService() {

    }

    private static void printStatus() {

    }

    private static void printConfig() {
        System.out.println("1M5 Proxy Configuration:");
        Set<String> names = config.stringPropertyNames();
        for(String n : names) {
            System.out.println("\t"+n+"  : "+config.getProperty(n));
        }
    }

    protected static boolean loadLoggingProperties(Properties p) {
        String logPropsPathStr = p.getProperty("java.util.logging.config.file");
        if(logPropsPathStr != null) {
            File logPropsPathFile = new File(logPropsPathStr);
            if(logPropsPathFile.exists()) {
                try {
                    FileInputStream logPropsPath = new FileInputStream(logPropsPathFile);
                    LogManager.getLogManager().readConfiguration(logPropsPath);
                    return true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

}
