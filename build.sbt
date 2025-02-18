import play.sbt.PlayImport.PlayKeys
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin

val silencerVersion = "1.7.8"

lazy val microservice = Project("service-dependencies", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(majorVersion := 2)
  .settings(SbtDistributablesPlugin.publishingSettings: _*)
  .settings(PlayKeys.devSettings += "play.server.netty.maxInitialLineLength" -> "65536")
  .settings(PlayKeys.playDefaultPort := 8459)
  .settings(libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test)
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(scalaVersion := "2.13.8")
  .settings(
    // Use the silencer plugin to suppress warnings from unused imports in routes etc.
    scalacOptions += "-P:silencer:pathFilters=routes;resources",
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
      "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
    )
  )
  .settings(
    Test / resources := (Test / resources).value ++ Seq(baseDirectory.value / "conf" / "application.conf")
  )
