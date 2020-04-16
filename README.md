# Content

  * [Content](#content)
  * [WoofPlot](#woofplot)
    * [Requirements](#requirements)
    * [Installation](#installation)
      * [Docker Image](#docker-image)
      * [Executable](#executable)
  * [Running WoofPlot](#running-woofplot)

# WoofPlot
Time series extraction, aggregation, and plotting platform. WoofPlot's responsibility is to provide:
1. Frontend web interface for plotting time series data and configuring sources from which to extract data
2. Backend server to keep configured data sources synchronized, extract time series data, and host the frontend

## Requirements
* [Java 8](https://openjdk.java.net/install/)
* [sbt](https://www.scala-sbt.org/1.x/docs/Setup.html)
* [yarn](https://classic.yarnpkg.com/en/docs/install)
* [PostgreSQL with TimescaleDB extension](https://docs.timescale.com/latest/getting-started/setup) (only if running WoofPlot binary outside of container)
* [Docker](https://docs.docker.com/get-docker/) (only if building and running the WoofPlot image)

## Installation

### 
