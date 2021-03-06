package de.dimajix.training.spark.jdbc

import java.util.Properties

import scala.collection.JavaConversions._

import org.apache.spark.sql.SparkSession
import org.kohsuke.args4j.CmdLineException
import org.kohsuke.args4j.CmdLineParser
import org.kohsuke.args4j.Option
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
  * Created by kaya on 03.12.15.
  */
object ExportDriver {
  def main(args: Array[String]) : Unit = {
    // First create driver, so can already process arguments
    val driver = new ExportDriver(args)

    // Now create SparkContext (possibly flooding the console with logging information)
    val sql = SparkSession
        .builder()
        .appName("Spark JDBC Exporter")
        .getOrCreate()

    // ... and run!
    driver.run(sql)
  }
}


class ExportDriver(args: Array[String]) {
  private val logger: Logger = LoggerFactory.getLogger(classOf[ExportDriver])

  @Option(name = "--weather", usage = "weather dirs", metaVar = "<weatherDirectory>")
  private var inputPath: String = "data/weather/2005,data/weather/2006,data/weather/2007,data/weather/2008,data/weather/2009,data/weather/2010,data/weather/2011,data/weather/2012"
  @Option(name = "--stations", usage = "stations definitioons", metaVar = "<stationsPath>")
  private var stationsPath: String = "data/weather/isd"
  @Option(name = "--dburi", usage = "JDBC connection", metaVar = "<connection>")
  private var dburi: String = "jdbc:mysql://localhost/training"
  @Option(name = "--dbuser", usage = "JDBC username", metaVar = "<db_user>")
  private var dbuser: String = "cloudera"
  @Option(name = "--dbpass", usage = "JDBC password", metaVar = "<db_password>")
  private var dbpassword: String = "cloudera"

  parseArgs(args)

  private def parseArgs(args: Array[String]) {
    val parser: CmdLineParser = new CmdLineParser(this)
    parser.setUsageWidth(80)
    try {
      parser.parseArgument(args.toList)
    }
    catch {
      case e: CmdLineException => {
        System.err.println(e.getMessage)
        parser.printUsage(System.err)
        System.err.println
        System.exit(1)
      }
    }
  }

  def run(sql: SparkSession) = {
    // Setup connection properties for JDBC
    val dbprops = new Properties
    dbprops.setProperty("user", dbuser)
    dbprops.setProperty("password", dbpassword)
    dbprops.setProperty("driver", "com.mysql.jdbc.Driver")

    // Load Weather data
    val raw_weather = sql.sparkContext.textFile(inputPath)
    val weather_rdd = raw_weather.map(WeatherData.extract)
    val weather = sql.createDataFrame(weather_rdd, WeatherData.schema)

    // Write data into DB via JDBC
    weather.write.jdbc(dburi, "weather", dbprops)

    // Load station data
    val isd_raw = sql.sparkContext.textFile(stationsPath)
    val isd_head = isd_raw.first
    val isd_rdd = isd_raw
      .filter(_ != isd_head)
      .map(StationData.extract)
    val isd = sql.createDataFrame(isd_rdd, StationData.schema)

    // Write data into DB via JDBC
    isd.write.jdbc(dburi, "isd", dbprops)
  }
}
