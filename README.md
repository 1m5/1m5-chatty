# 1M5 Chatty
Starts the 1M5 daemon for Purism Chatty.
- Either download 1m5-chatty.deb from releases or build it yourself (below).
- Ensure OpenJDK 8 or above is installed.
    - Linux: https://openjdk.java.net/install/
    - Mac: https://www.youtube.com/watch?v=y6szNJ4rMZ0
    - Windows: https://www.youtube.com/watch?v=74pE3kLerAU&vl=en

## Releases
When downloading releases, please ensure you're downloading the correct 1m5-chatty.deb from the network you wish to connect to:
- **devnet**: development
- **demonet**: demos (should only be used by Purism)
- **integrationnet**: automated quality assurance integrated testing (CI use only)
- **testnet**: testing Purism apps integration prior to connecting to mainnet
- **mainnet**: live production network

https://github.com/1m5/1m5-chatty/releases

## Builds
1. First build the 1M5 core by cloning the 1m5-core repo and all dependent repos (look in pom.xml).
2. Build all repos in order of dependency starting with 1m5-data and ending with 1m5-chatty.
3. NOTE: Ensure you set the correct maven profiles for 1m5-chatty.

### Maven Profiles
Build project with your appropriate network set in maven profile:
- **devnet**: development
- **demonet**: demos (should only be used by Purism)
- **testnet**: testing Purism apps integration prior to connecting to mainnet
- **mainnet**: live production network

## Install

