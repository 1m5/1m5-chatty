# 1M5 Proxy
Starts the 1M5 daemon as a proxy.
- Ensure OpenJDK 8 or above is installed.
    - Linux: https://openjdk.java.net/install/
    - Mac: https://www.youtube.com/watch?v=y6szNJ4rMZ0
    - Windows: https://www.youtube.com/watch?v=74pE3kLerAU&vl=en

## Releases
When downloading releases, please ensure you're downloading the correct 1m5-proxy.jar from the network you wish to connect to:
- **devnet**: development
- **demonet**: demos 
- **integrationnet**: automated quality assurance integrated testing (CI use only)
- **testnet**: testing 1M5 dapps integration prior to connecting to mainnet
- **mainnet**: live production network

https://github.com/1m5/1m5-proxy/releases

Currently, only devnet is supported.

## Build

### Install OpenJDK 8 or above

### Install latest Maven

### Install latest Git

### Build Git Repositories in order
Create a folder in your home directory (~) called Projects.
Within in Projects, create a folder called 1M5.
For each repository below, 

1. 1m5-data
2. 1m5-core
3. 1m5-did
4. 1m5-sensors
5. 1m5-neo4j
6. 1m5-sensormanager-neo4j
7. 1m5-i2p
8. 1m5-tor-client
9. 1m5-clearnet-server
10. 1m5-proxy

### Maven Profiles
Build project with your appropriate network set in maven profile:
- **devnet**: development
- **demonet**: demos (should only be used by Purism)
- **integrationnet**: CI
- **testnet**: testing Purism apps integration prior to connecting to mainnet
- **mainnet**: live production network

## Install
**Must be admin/super user**

**Must not have another instance of I2P running. Can check by running 'i2prouter status'. If running, shutdown with 'i2prouter stop'.**

### Linux Setup
**Notes**: Tested on CentOS 7, Purism PureOS (Debian), Ubuntu (Debian), and Raspian (Raspberry Pi Debian).

#### User Install Directory Setup (tested)
1. Create 1m5 folder with proxy folder in current user home, e.g. /home/[user]/1m5/proxy
2. Download the 1m5-proxy-lib.zip and 1m5-proxy-0.6.1-SNAPSHOT.jar from [releases](https://github.com/1m5/1m5-proxy/releases/tag/0.6.1-alpha).
3. Unzip 1m5-proxy-lib.zip into /home/[user]/1m5/proxy/lib then move 1m5-proxy-0.6.1-SNAPSHOT.jar into /home/[user]/1m5/proxy/lib
4. Download the appropriate 1m5-proxy.service from [releases](https://github.com/1m5/1m5-proxy/releases/tag/0.6.1-alpha)
    and save to /usr/lib/systemd/system (CentOS) folder or /lib/systemd/system (Debian) as 1m5-proxy.service
5. sudo systemctl daemon-reload
6. sudo systemctl start 1m5-proxy.service 
7. If you wish the service to auto-start on operating system startup: sudo systemctl enable 1m5-proxy.service
8. If you wish to make 1m5-proxy more performant or I2P status in logs shows that it's blocked:
    1. wait about 20 minutes for I2P to establish base file sets and making initial connection attempts
    2. stop the program (sudo systemctl stop 1m5-proxy)
    3. Open /home/[user]/1m5/proxy/.1m5/services/io.onemfive.sensors.SensorService/sensors/i2p/router.config and then open i2np.udp.port in firewalld for both udp and tcp (https://www.digitalocean.com/community/tutorials/how-to-set-up-a-firewall-using-firewalld-on-centos-7)
    4. Start program (systemctl start 1m5-proxy)
9. Verify 1M5 ready for requests after about 5 minutes in 1m5-proxy-log-0.txt
10. Set this 1M5 proxy as your proxy for the browser you wish to use with it.
