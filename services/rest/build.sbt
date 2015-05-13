name := """rest"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.11.1"

testNGSettings

libraryDependencies ++= Seq(
  cache,
	"org.ats" % "cloud-common" % "1.0.0-Alpha-1-SNAPSHOT",
	"org.ats.services" % "database" % "1.0.0-Alpha-1-SNAPSHOT",
	"org.ats.services" % "organization" % "1.0.0-Alpha-1-SNAPSHOT",
	"org.ats.services" % "event" % "1.0.0-Alpha-1-SNAPSHOT"
)

resolvers ++= Seq(
   Resolver.sonatypeRepo("snapshots"),
   Resolver.mavenLocal
)
