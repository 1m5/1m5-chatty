package io.onemfive.proxy;

import io.onemfive.clearnet.server.ClearnetServerSensor;
import io.onemfive.core.Config;
import io.onemfive.core.OneMFiveAppContext;
import io.onemfive.core.admin.AdminService;
import io.onemfive.core.client.Client;
import io.onemfive.core.client.ClientAppManager;
import io.onemfive.core.client.ClientStatusListener;
import io.onemfive.data.Envelope;
import io.onemfive.data.util.DLC;
import io.onemfive.did.DIDService;
import io.onemfive.i2p.I2PSensor;
import io.onemfive.sensormanager.neo4j.SensorManagerNeo4j;
import io.onemfive.sensors.Sensor;
import io.onemfive.sensors.SensorManager;
import io.onemfive.sensors.SensorsService;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class Daemon {

    private static final Logger LOG = Logger.getLogger(Daemon.class.getName());

    private enum Status {Shutdown, Initializing, Initialized, Starting, Running, ShuttingDown, Errored, Exiting}

    private static final Daemon instance = new Daemon();

    private static ClientAppManager manager;
    private static ClientAppManager.Status clientAppManagerStatus;
    private static Client client;
    private static Properties config;
    private static boolean running = false;
    private static Scanner scanner;
    private static Status status = Status.Shutdown;

    public static void main(String[] args) {
        System.out.println("Welcome to 1M5 Chatty Daemon. Starting 1M5 Service...");
        Properties p = new Properties();
        String[] parts;
        for(String arg : args) {
            parts = arg.split("=");
            p.setProperty(parts[0],parts[1]);
        }

        loadLoggingProperties(p);

        try {
            config = Config.loadFromClasspath("1m5-chatty.config", p, false);
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
                instance.launch();
                // Check periodically to see if 1M5 stopped
                while (instance.clientAppManagerStatus != ClientAppManager.Status.STOPPED) {
                    instance.waitABit(2 * 1000);
                }
            } catch (Exception e) {
                e.printStackTrace();
                LOG.severe(e.getLocalizedMessage());
                System.exit(-1);
            }
//        }

        System.out.println("1M5 Chatty Daemon exiting...");
        System.exit(0);
    }

    private void launch() throws Exception {

        // Directories
        String rootDir = config.getProperty("1m5-chatty.dir.base");
        if(rootDir == null) {
            rootDir = System.getProperty("user.dir") + "/chat";
            config.setProperty("1m5-chatty.dir.base",rootDir);
        }
        File ikFolder = new File(rootDir);
        if(!ikFolder.exists())
            if(!ikFolder.mkdir())
                throw new Exception("Unable to create 1M5-Chatty directory: "+rootDir);
        LOG.config("1M5-Chatty Root Directory: "+rootDir);

        String oneMFiveDir = rootDir + "/.1m5";
        File oneMFiveFolder = new File(oneMFiveDir);
        if(!oneMFiveFolder.exists())
            if(!oneMFiveFolder.mkdir())
                throw new Exception("Unable to create 1M5 base directory: "+oneMFiveDir);
        config.setProperty("1m5.dir.base",oneMFiveDir);
        LOG.config("1M5 Root Directory: "+oneMFiveDir);

        // Start DCDN Service
        String nodeUsername = config.getProperty("username");
        if(nodeUsername==null) {
            LOG.severe("node username required.");
            return;
        }
        String nodePassphrase = config.getProperty("passphrase");
        if(nodePassphrase == null) {
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
    }

    private static void waitABit(long waitTime) {
        try {
            Thread.sleep(waitTime);
        } catch (InterruptedException e) {}
    }

    private static void printMenu() {
        System.out.println("The following commands are available: ");
        switch(status) {
            case Shutdown: {
                System.out.println("\tin - Initializing the 1M5 Chatty Daemon.");
                System.out.println("\tq  - Quit.");
                break;
            }
            case Initialized: {
                System.out.println("\tst - Start the 1M5 Chatty Daemon.");
                System.out.println("\tc  - Print Config.");
                System.out.println("\tq  - Quit.");
                break;
            }
            case Running: {
                System.out.println("\tsd - Shutdown the 1M5 Chatty Daemon.");
                System.out.println("\tc  - Print Config.");
            }
        }
    }

    private static void processCommand(String command) {
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
                System.out.println("Exiting 1M5 Chatty Daemon....");
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
                System.out.println("Initializing 1M5 Chatty Daemon....");
                status = Status.Initializing;
                try {
                    initialize();
                    status = Status.Initialized;
                    System.out.println("1M5 Chatty Daemon Initialized.");
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("1M5 Chatty Daemon failed to Initialize.");
                }
                break;
            }
            case "st": {
                if(status != Status.Initialized) {
                    System.out.println("Command not available.");
                    printMenu();
                    return;
                }
                System.out.println("Starting 1M5 Chatty Daemon....");
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
                System.out.println("1M5 Chatty Daemon Running.");
                break;
            }
            case "sd": {
                if(status != Status.Running) {
                    System.out.println("Command not available.");
                    printMenu();
                    return;
                }
                System.out.println("Shutting Down 1M5 Chatty Daemon....");
                status = Status.ShuttingDown;
                stopService();
                status = Status.Shutdown;
                System.out.println("1M5 Chatty Daemon Shutdown.");
                break;
            }
        }
    }

    private static void initialize() throws Exception {
        // Directories
        String rootDir = config.getProperty("1m5-chatty.dir.base");
        if(rootDir == null) {
            rootDir = System.getProperty("user.dir") + "/chat";
            config.setProperty("1m5-chatty.dir.base",rootDir);
        }
        File ikFolder = new File(rootDir);
        if(!ikFolder.exists())
            if(!ikFolder.mkdir())
                throw new Exception("Unable to create 1M5 Chatty directory: "+rootDir);
        System.out.println("1M5 Chatty Root Directory: "+rootDir);

        String oneMFiveDir = rootDir + "/.1m5";
        File oneMFiveFolder = new File(oneMFiveDir);
        if(!oneMFiveFolder.exists())
            if(!oneMFiveFolder.mkdir())
                throw new Exception("Unable to create 1M5 base directory: "+oneMFiveDir);
        config.setProperty("1m5.dir.base",oneMFiveDir);
        System.out.println("1M5 Root Directory: "+oneMFiveDir);
    }

    private static void startService(String nodeUsername, String nodePassphrase) {
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
        System.out.println("1M5 Chatty Configuration:");
        Set<String> names = config.stringPropertyNames();
        for(String n : names) {
            System.out.println("\t"+n+"  : "+config.getProperty(n));
        }
    }

    private static boolean loadLoggingProperties(Properties p) {
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
