# Content

  * [Content](#content)
  * [WoofPlot](#woofplot)
    * [Requirements](#requirements)
    * [Installation](#installation)
      * [Docker Image](#docker-image)
      * [Executable](#executable)
      * [MacOS App](#macos-app)
  * [Running WoofPlot](#running-woofplot)
      * [Docker Compose](#with-docker-compose)
      * [Startup Script](#with-startup-script)

# WoofPlot
Time series extraction, aggregation, and plotting platform. WoofPlot's responsibility is to provide:
1. Frontend web interface for plotting time series data and configuring sources from which to extract data
2. Backend server to keep configured data sources synchronized, extract time series data, and host the frontend

## Requirements
* [Java 8](https://openjdk.java.net/install/)
* [sbt](https://www.scala-sbt.org/1.x/docs/Setup.html)
* [yarn](https://classic.yarnpkg.com/en/docs/install)
* [PostgreSQL with TimescaleDB extension](https://docs.timescale.com/latest/getting-started/setup) (if running WoofPlot binary outside of container)
* [Docker](https://docs.docker.com/get-docker/) (if building and running the WoofPlot image)

## Installation

### Docker Image
To build a Docker image for WoofPlot and publish it to your local repository, run `sbt docker:publishLocal` in the project root directory.

### Executable
To build a standalone executable for WoofPlot, run `sbt stage` in the project. Scripts for Linux and Windows systems will be generated in `target/universal/stage/bin`. **Note: the generated scripts are __NOT__ portable.** 

### MacOS App
WoofPlot can alternatively be built as a MacOS desktop application with the JRE bundled. This requires use of a [backported version of the new Java Packager](https://mail.openjdk.java.net/pipermail/openjfx-dev/2018-September/022500.html). While the exact build process will vary depending on one's MacOS environment and installation, a rough guide to the build looks like:
1. Generate a fat jar:
```
sbt assembly
```
2. Navigate to target directory:
```
cd target/scala-2.13/
```
3. Generate a MacOS application (`JAVA_HOME` needs to be set to <jdk-11>/Contents/Home): 
```
/opt/jdkpackager-11/jpackager create-image\
-j woofplot-assembly-0.0.1-SNAPSHOT.jar --output . -i . --verbose -f woofplot-assembly-0.0.1-SNAPSHOT.jar \
--add-modules java.base,java.datatransfer,java.desktop,java.management,java.logging,jdk.unsupported,java.naming \
--name WoofPlot --jvm-args '-Dapple.awt.application.name=WoofPlot -Dgui=true' --mac-bundle-identifier edu.ucsb.woofplot
```
4. Codesign the app: 
```
codesign --timestamp --options runtime  --entitlements ../../bundler/macos/woofplot.entitlements  --sign "Developer ID Application"  --force --deep --verbose WoofPlot.app
```
5. Prepare the DMG installer:
```
mkdir temp
ditto WoofPlot.app temp/WoofPlot.app
ln -s /Applications/ temp/Applications
mkdir -p temp/.background
cp ../../bundler/macos/background.png temp/.background/background.png 
hdiutil create -srcfolder temp -volname "WoofPlot" -fs HFS+ -format UDRW temp.dmg
hdiutil attach -readwrite -noverify -noautoopen temp.dmg

osascript ../../bundler/macos/dragndrop.applescript WoofPlot
chmod -Rf go-w /Volumes/WoofPlot
bless --folder /Volumes/WoofPlot --openfolder /Volumes/WoofPlot
SetFile -a C /Volumes/WoofPlot
hdiutil detach disk2s1
hdiutil convert temp.dmg -format UDZO -imagekey zlib-level=9 -o WoofPlot.dmg
```
6. Codesign the DMG:
```
codesign -s "Developer ID Application" WoofPlot.dmg
```
7. Submit DMG for notarization to Apple:
```
xcrun altool --notarize-app --primary-bundle-id edu.ucsb.woofplot --file WoofPlot.dmg -u me@keremc.com -p "@keychain:altool-kerem"
```
8. Check status of notarization:
```
xcrun altool --notarization-info <REQUEST UUID> -u me@keremc.com -p "@keychain:altool-kerem"
```
9. Once approved, staple ticket to distribution:
```
 xcrun stapler staple WoofPlot.dmg 
``` 

# Running WoofPlot

## With Docker Compose
A `docker-compose.yml` file exists in the root directory. If Docker Compose is installed, the WoofPlot services (webapp & database) can be started with `docker-compose up`. This will start and expose the webapp on port 8080, and the PostgreSQL database on port 5432. These ports and various other configuration values can be tweaked in `docker-compose.yml`

## With Startup Script
The `target/universal/stage/bin/woofplot` script can be used to start the webapp after it has been fully staged with `sbt stage`. The standalone webapp by default attempts to connect to `localhost:5432` with credentials `postgres:root`, this and other values in `conf/application.conf` can be tweaked by passing system properties equal to the fully-qualified name of the configuration key, e.g. `-Dslick.dbs.default.db.url=/dev/null`
