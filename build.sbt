scalaVersion := "2.11.12"

lazy val `push-notifications-service` = project in file(".") enablePlugins Raml2Hyperbus settings (
  name := "push-notifications-service",
  version := "0.1-SNAPSHOT",
  organization := "com.hypertino",
  resolvers ++= Seq(
    Resolver.sonatypeRepo("public")
  ),
  libraryDependencies ++= Seq(
    "com.hypertino" %% "hyperbus" % "0.5-SNAPSHOT",
    "com.hypertino" %% "hyperbus-t-inproc" % "0.5-SNAPSHOT" % "test",
    "com.hypertino" %% "service-control" % "0.3.1",
    "com.hypertino" %% "service-config" % "0.2.3" % "test",

    "ch.qos.logback" % "logback-classic" % "1.2.3" % "test",
    "org.scalamock" %% "scalamock-scalatest-support" % "3.5.0" % "test",

    "com.turo" % "pushy" % "0.11.3" excludeAll ExclusionRule(organization = "io.netty"),

    compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
  ),
  ramlHyperbusSources := Seq(
    ramlSource(
      path = "api/apns-service-api/apns-api.raml",
      packageName = "com.hypertino.services.apns.api",
      isResource = false
    ),
    ramlSource(
      path = "api/push-notifications-service-api/push-notifications-api.raml",
      packageName = "com.hypertino.services.pushnotifications.api",
      isResource = false
    ),
    ramlSource(
      path = "api/hyperstorage-service-api/hyperstorage.raml",
      packageName = "com.hypertino.services.hyperstorage.api",
      isResource = false
    )
  )
)

