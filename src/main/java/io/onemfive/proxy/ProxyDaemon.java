package io.onemfive.proxy;

import io.onemfive.clearnet.server.ClearnetServerSensor;
import io.onemfive.core.Config;
import io.onemfive.core.OneMFiveAppContext;
import io.onemfive.core.admin.AdminService;
import io.onemfive.core.client.Client;
import io.onemfive.core.client.ClientAppManager;
import io.onemfive.core.client.ClientStatusListener;
import io.onemfive.core.util.SystemSettings;
import io.onemfive.data.*;
import io.onemfive.data.util.DLC;
import io.onemfive.data.util.FileUtil;
import io.onemfive.did.DIDService;
import io.onemfive.i2p.I2PSensor;
import io.onemfive.sensors.Sensor;
import io.onemfive.sensors.SensorManager;
import io.onemfive.sensors.SensorManagerUncensored;
import io.onemfive.sensors.SensorsService;
import io.onemfive.tor.client.TorClientSensor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * 1M5 Daemon as a Proxy
 *
 * @since 0.6.1
 * @author objectorange
 */
public class ProxyDaemon {

    private static final Logger LOG = Logger.getLogger(ProxyDaemon.class.getName());

    private enum Status {Shutdown, Initializing, Initialized, Starting, Running, ShuttingDown, Errored, Exiting}

    protected static Properties config;
    private static final ProxyDaemon instance = new ProxyDaemon();
    private static ClientAppManager manager;
    private static ClientAppManager.Status clientAppManagerStatus;
    private static Client client;

    protected Status status = Status.Shutdown;

    public static File oneMFiveCoreDir;
    public static File oneMFiveProxyDir;
    public static File userAppDataDir;
    public static File userAppConfigDir;
    public static File userAppCacheDir;

    public static void main(String[] args) {
        System.out.println("Welcome to 1M5 Proxy Daemon. Starting 1M5 Proxy Service...");
        Properties p = new Properties();
        String[] parts;
        for(String arg : args) {
            parts = arg.split("=");
            p.setProperty(parts[0],parts[1]);
        }
        // Logging
        loadLoggingProperties(p);
        // Config
        try {
            config = Config.loadFromClasspath("1m5-proxy.config", p, false);
        } catch (Exception e) {
            System.out.println(e.getLocalizedMessage());
            System.exit(-1);
        }
        try {
            instance.launch();
            // Check periodically to see if 1M5 stopped
            while (instance.clientAppManagerStatus != ClientAppManager.Status.STOPPED) {
                instance.waitABit(2 * 1000);
            }
        } catch (Exception e) {
            LOG.severe(e.getLocalizedMessage());
            System.exit(-1);
        }
        System.out.println("1M5 Proxy Daemon exiting...");
        System.exit(0);
    }

    public void launch() throws Exception {

        // Directories
        String oneMFiveCoreDirStr = config.getProperty("1m5.dir.base");
        if(oneMFiveCoreDirStr!=null) {
            oneMFiveCoreDir = new File(oneMFiveCoreDirStr);
            if(!oneMFiveCoreDir.exists() && !oneMFiveCoreDir.mkdir()) {
                throw new Exception("Unable to create 1m5.dir.base: "+oneMFiveCoreDirStr);
            }
        }  else {
            oneMFiveCoreDir = SystemSettings.getSystemApplicationDir("1m5", "core", true);
            if (oneMFiveCoreDir == null) {
                throw new Exception("Unable to create base system directory for 1M5 core.");
            } else {
                oneMFiveCoreDirStr = oneMFiveCoreDir.getAbsolutePath();
                config.put("1m5.dir.base", oneMFiveCoreDirStr);
            }
        }

        String oneMFiveProxyDirStr = config.getProperty("1m5.proxy.dir.base");
        if(oneMFiveProxyDirStr!=null) {
            oneMFiveProxyDir = new File(oneMFiveProxyDirStr);
            if(!oneMFiveProxyDir.exists() && !oneMFiveProxyDir.mkdir()) {
                throw new Exception("Unable to create supplied 1m5.dir.base directory: "+oneMFiveProxyDirStr);
            }
            userAppDataDir = new File(oneMFiveProxyDir, "data");
            if(!userAppDataDir.exists() && !userAppDataDir.mkdir()) {
                throw new Exception("Unable to create user app data directory: "+oneMFiveProxyDir.getAbsolutePath() + "/data");
            } else {
                config.setProperty("inkrypt.dcdn.dir.userAppData", userAppDataDir.getAbsolutePath());
            }

            userAppConfigDir = new File(oneMFiveProxyDir, "config");
            if(!userAppConfigDir.exists() && !userAppConfigDir.mkdir()) {
                throw new Exception("Unable to create user app config directory: "+oneMFiveProxyDir.getAbsolutePath() + "/config");
            } else {
                config.setProperty("inkrypt.dcdn.dir.userAppConfig", userAppConfigDir.getAbsolutePath());
            }

            userAppCacheDir = new File(oneMFiveProxyDir, "cache");
            if(!userAppCacheDir.exists() && !userAppCacheDir.mkdir()) {
                throw new Exception("Unable to create user app cache directory: "+oneMFiveProxyDir.getAbsolutePath() + "/cache");
            } else {
                config.setProperty("inkrypt.dcdn.dir.userAppCache", userAppCacheDir.getAbsolutePath());
            }
        } else {
            oneMFiveProxyDir = SystemSettings.getSystemApplicationDir("1m5", "proxy", true);
            if (oneMFiveProxyDir == null) {
                throw new Exception("Unable to create system directory for 1M5 Proxy dapp.");
            } else {
                config.setProperty("1m5.proxy.dir.base", oneMFiveProxyDir.getAbsolutePath());
            }
            userAppDataDir = SystemSettings.getUserAppDataDir("1m5", "proxy", true);
            config.setProperty("1m5.proxy.dir.userAppData", userAppDataDir.getAbsolutePath());

            userAppConfigDir = SystemSettings.getUserAppConfigDir("1m5", "proxy", true);
            config.setProperty("1m5.proxy.dir.userAppConfig", userAppConfigDir.getAbsolutePath());

            userAppCacheDir = SystemSettings.getUserAppCacheDir("1m5", "proxy", true);
            config.setProperty("1m5.proxy.dir.userAppCache", userAppCacheDir.getAbsolutePath());
        }
        LOG.info("1M5 Proxy Dapp Directories: " +
                "\n\tBase: " + oneMFiveProxyDir.getAbsolutePath() +
                "\n\tData: " + userAppDataDir.getAbsolutePath() +
                "\n\tConfig: " + userAppConfigDir.getAbsolutePath() +
                "\n\tCache: " + userAppCacheDir.getAbsolutePath());

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
        services.add(ProxyService.class);
        services.add(DIDService.class);
        services.add(SensorsService.class);

        // Setup SensorManagerNeo4j - SensorManagerUncensored is default
//        config.setProperty(SensorManager.class.getName(), SensorManagerUncensored.class.getName());

        // Setup Sensors
        // I2P
        String sensorsConfig = I2PSensor.class.getName()+","+Envelope.Sensitivity.HIGH.name()+",100";
        // Tor
        sensorsConfig += ":"+ TorClientSensor.class.getName()+","+Envelope.Sensitivity.MEDIUM.name()+",100";
        // Localhost HTTP
        sensorsConfig += ":"+ ClearnetServerSensor.class.getName()+","+Envelope.Sensitivity.NONE.name()+",100";
        config.setProperty(Sensor.class.getName(), sensorsConfig);

        DLC.addEntity(services, e);
        DLC.addData(Properties.class, config, e);

        // Register Services
        DLC.addRoute(AdminService.class, AdminService.OPERATION_REGISTER_SERVICES,e);
        client.request(e);

    }

    protected static boolean loadLoggingProperties(Properties p) {
        String logPropsPathStr = p.getProperty("java.util.logging.config.file");
        if(logPropsPathStr!=null) {
            File logPropsPathFile = new File(logPropsPathStr);
            if(logPropsPathFile.exists()) {
                try {
                    FileInputStream logPropsPath = new FileInputStream(logPropsPathFile);
                    LogManager.getLogManager().readConfiguration(logPropsPath);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("handlers = java.util.logging.FileHandler, java.util.logging.ConsoleHandler\n");
                sb.append(".level="+p.getProperty("log.level")+"\n");
                sb.append("java.util.logging.FileHandler.pattern = 1m5-proxy-%u.log\n");
                sb.append("java.util.logging.FileHandler.limit = 1000000\n");
                sb.append("java.util.logging.FileHandler.count = 1\n");
                if(p.getProperty("log.level")==null) {
                    p.setProperty("log.level", "SEVERE");
                }
                sb.append("java.util.logging.FileHandler.level = "+p.getProperty("log.level")+"\n");
                sb.append("java.util.logging.FileHandler.formatter = java.util.logging.SimpleFormatter\n");
                sb.append("java.util.logging.ConsoleHandler.level = ALL\n");
                sb.append("java.util.logging.ConsoleHandler.formatter = java.util.logging.SimpleFormatter\n");
                FileUtil.writeFile(sb.toString().getBytes(), logPropsPathStr);
            }
        }
        return true;
    }

    protected void waitABit(long waitTime) {
        try {
            Thread.sleep(waitTime);
        } catch (InterruptedException e) {}
    }

}
