# Content

  * [Content](#content)
  * [WoofPlot](#woofplot)
    * [Requirements](#requirements)
    * [Installation](#installation)
      * [Docker Image](#docker-image)
      * [Executable](#executable)
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

# Running WoofPlot

## With Docker Compose
A `docker-compose.yml` file exists in the root directory. If Docker Compose is installed, the WoofPlot services (webapp & database) can be started with `docker-compose up`. This will start and expose the webapp on port 8080, and the PostgreSQL database on port 5432. These ports and various other configuration values can be tweaked in `docker-compose.yml`

## With Startup Script
The `target/universal/stage/bin/woofplot` script can be used to start the webapp after it has been fully staged with `sbt stage`. The standalone webapp by default attempts to connect to `localhost:5432` with credentials `postgres:root`, this and other values in `conf/application.conf` can be tweaked by passing system properties equal to the fully-qualified name of the configuration key, e.g. `-Dslick.dbs.default.db.url=/dev/null`
