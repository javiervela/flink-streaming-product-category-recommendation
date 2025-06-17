import sbtassembly.AssemblyPlugin
import sbtassembly.AssemblyPlugin.autoImport._

enablePlugins(AssemblyPlugin)

ThisBuild / scalaVersion := "2.12.18"
ThisBuild / version := "0.1"
ThisBuild / organization := "es.uimp.bigdata"

lazy val flinkVersion = "1.20.0"
lazy val kafkaConnectorVersion = "3.3.0-1.20"
lazy val json4sVersion = "4.0.6"
lazy val pmmlVersion = "1.7.3"

name := "flinkRecommendationPipeline"

libraryDependencies ++= Seq(
  // Flink core dependencies
  "org.apache.flink" %% "flink-streaming-scala" % flinkVersion % "provided",
  "org.apache.flink" %% "flink-scala" % flinkVersion % "provided",
  "org.apache.flink" % "flink-clients" % flinkVersion % "provided",

  // Flink connectors
  "org.apache.flink" % "flink-connector-base" % flinkVersion,
  "org.apache.flink" % "flink-connector-kafka" % kafkaConnectorVersion,

  // PMML dependencies
  "org.jpmml" % "pmml-evaluator" % pmmlVersion,
  "org.jpmml" % "pmml-model" % pmmlVersion,

  // JSON processing
  "org.json4s" %% "json4s-jackson" % json4sVersion,

  // Configuration and utilities
  "io.github.cdimascio" % "dotenv-java" % "2.2.4",

  // XML binding for PMML
  "jakarta.xml.bind" % "jakarta.xml.bind-api" % "3.0.1",
  "org.glassfish.jaxb" % "jaxb-runtime" % "3.0.2",

  // Logging
  "org.slf4j" % "slf4j-log4j12" % "1.7.32"
)

// Assembly configuration
assembly / mainClass := Some("es.uimp.bigdata.flink.RecommendationJob")
assembly / assemblyJarName := "flink_recommendation_pipeline.jar"

// Resolve version conflicts
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "io.netty.versions.properties") =>
    MergeStrategy.discard
  case PathList("META-INF", "versions", "9", "module-info.class") =>
    MergeStrategy.discard
  case PathList("META-INF", "services", xs @ _*) =>
    MergeStrategy.filterDistinctLines
  case PathList("META-INF", "MANIFEST.MF") =>
    MergeStrategy.discard
  case PathList("META-INF", "INDEX.LIST") =>
    MergeStrategy.discard
  case PathList("META-INF", "DEPENDENCIES") =>
    MergeStrategy.discard
  case PathList("META-INF", "LICENSE*") =>
    MergeStrategy.discard
  case PathList("META-INF", "NOTICE*") =>
    MergeStrategy.discard
  case "module-info.class" =>
    MergeStrategy.discard
  case "reference.conf" =>
    MergeStrategy.concat
  case PathList("org", "apache", "commons", "logging", xs @ _*) =>
    MergeStrategy.first
  case x if x.endsWith(".proto") =>
    MergeStrategy.first
  case x =>
    MergeStrategy.first
}

// Resolver configuration
resolvers ++= Seq(
  "Apache Flink" at "https://repository.apache.org/content/repositories/releases/",
  "Maven Central" at "https://repo1.maven.org/maven2"
)

// Compiler options
scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint"
)

// Runtime configuration
run / fork := true
run / javaOptions ++= Seq(
  "-Xmx2G",
  "-XX:+UseG1GC"
)
