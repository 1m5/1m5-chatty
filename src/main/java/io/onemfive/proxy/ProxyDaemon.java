package io.onemfive.proxy;

import io.onemfive.core.Config;
import io.onemfive.core.client.ClientAppManager;
import io.onemfive.core.util.AppThread;
import io.onemfive.core.util.SystemSettings;
import io.onemfive.data.Hash;
import io.onemfive.data.util.FileUtil;
import io.onemfive.data.util.HashUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
    protected static ProxyDaemon proxyDaemon;
    protected static ProxyClient proxyClient;

    protected boolean running = false;
    protected Scanner scanner;
    protected Status status = Status.Shutdown;

    public static File oneMFiveCoreDir;
    public static File oneMFiveProxyDir;
    public static File userAppDataDir;
    public static File userAppConfigDir;
    public static File userAppCacheDir;

    public static void main(String[] args) {
        System.out.println("Welcome to 1M5 Proxy Daemon. Starting 1M5 Service...");
        proxyDaemon = new ProxyDaemon();
        proxyDaemon.launch(args, "1m5-proxy.config");
        System.out.println("1M5 Proxy Daemon exiting...");
        System.exit(0);
    }

    public void launch(String[] args, String configFile) {
        Properties p = new Properties();
        String[] parts;
        for(String arg : args) {
            parts = arg.split("=");
            p.setProperty(parts[0],parts[1]);
        }

        try {
            config = Config.loadFromClasspath(configFile, p, false);
        } catch (Exception e) {
            System.out.println(e.getLocalizedMessage());
            System.exit(-1);
        }

        try {
            proxyDaemon.initialize();
            // launch ProxyClient in separate thread
            proxyDaemon.startService();
            // Check periodically to see if 1M5 stopped
            while (proxyClient.clientAppManagerStatus != ClientAppManager.Status.STOPPED) {
                proxyDaemon.waitABit(2 * 1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
            LOG.severe(e.getLocalizedMessage());
            System.exit(-1);
        }
//        }

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

    private boolean initialize() throws Exception {
        // Directories
        String oneMFiveCoreDirStr = config.getProperty("1m5.dir.base");
        if(oneMFiveCoreDirStr!=null) {
            oneMFiveCoreDir = new File(oneMFiveCoreDirStr);
            if(!oneMFiveCoreDir.exists() && !oneMFiveCoreDir.mkdir()) {
                LOG.warning("Unable to create 1m5.dir.base: "+oneMFiveCoreDirStr);
                return false;
            }
        }  else {
            oneMFiveCoreDir = SystemSettings.getSystemApplicationDir("1m5", "core", true);
            if (oneMFiveCoreDir == null) {
                LOG.severe("Unable to create base system directory for 1M5 core.");
                return false;
            } else {
                oneMFiveCoreDirStr = oneMFiveCoreDir.getAbsolutePath();
                config.put("1m5.dir.base", oneMFiveCoreDirStr);
            }
        }
        LOG.info("1M5 Base Directory: "+oneMFiveCoreDirStr);

        String oneMFiveProxyDirStr = config.getProperty("1m5.proxy.dir.base");
        if(oneMFiveProxyDirStr!=null) {
            oneMFiveProxyDir = new File(oneMFiveProxyDirStr);
            if(!oneMFiveProxyDir.exists() && !oneMFiveProxyDir.mkdir()) {
                throw new Exception("Unable to create supplied 1m5.dir.base directory: "+oneMFiveProxyDirStr);
            }
            userAppDataDir = new File(oneMFiveProxyDir.getAbsolutePath() + "/data");
            if(!userAppDataDir.exists() && !userAppDataDir.mkdir()) {
                throw new Exception("Unable to create user app data directory: "+oneMFiveProxyDir.getAbsolutePath() + "/data");
            } else {
                config.setProperty("inkrypt.dcdn.dir.userAppData", userAppDataDir.getAbsolutePath());
            }

            userAppConfigDir = new File(oneMFiveProxyDir.getAbsolutePath() + "/config");
            if(!userAppConfigDir.exists() && !userAppConfigDir.mkdir()) {
                throw new Exception("Unable to create user app config directory: "+oneMFiveProxyDir.getAbsolutePath() + "/config");
            } else {
                config.setProperty("inkrypt.dcdn.dir.userAppConfig", userAppConfigDir.getAbsolutePath());
            }

            userAppCacheDir = new File(oneMFiveProxyDir.getAbsolutePath() + "/cache");
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

        // Credentials
        String credFileStr = oneMFiveProxyDirStr + "/cred";
        File credFile = new File(credFileStr);
        if(!credFile.exists())
            if(!credFile.createNewFile())
                throw new Exception("Unable to create node credentials file at: "+credFileStr);

        config.setProperty("username","Alice235");
        String passphrase = FileUtil.readTextFile(credFileStr,1, true);
        if("".equals(passphrase) ||
                (config.getProperty("1m5.user.rebuild")!=null && "true".equals(config.getProperty("1m5.user.rebuild")))) {
            passphrase = HashUtil.generateHash(String.valueOf(System.currentTimeMillis()), Hash.Algorithm.SHA1).getHash();
            if(!FileUtil.writeFile(passphrase.getBytes(), credFileStr))
                return false;
            else
                LOG.info("New passphrase saved: "+passphrase);
        }
        config.setProperty("passphrase",passphrase);

        // Logging
        config.setProperty("java.util.logging.config.file",oneMFiveProxyDirStr+"/log/logging.properties");
        loadLoggingProperties(config);

        return true;
    }

    private void startService() {
        proxyClient = new ProxyClient();
        ProxyHandler.setProxyClient(proxyClient);
        new AppThread(new Runnable() {
            @Override
            public void run() {
                proxyClient.start(config);
            }
        }).start();
    }

    private void startService(String nodeUsername, String nodePassphrase) {
        // Start Proxy Client
        config.setProperty("username", nodeUsername);
        config.setProperty("passphrase", nodePassphrase);

        startService();
    }

    private static void stopService() {
        proxyClient.gracefulShutdown();
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
