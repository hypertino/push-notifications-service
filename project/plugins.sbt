resolvers ++= Seq(
  Resolver.sonatypeRepo("public")
)

addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.4.1")

addSbtPlugin("com.hypertino" % "hyperbus-raml-sbt-plugin" % "0.3-SNAPSHOT")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.7.0")