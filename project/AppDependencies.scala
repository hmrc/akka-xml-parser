import sbt._
object AppDependencies{

  val akkaVersion = "2.5.26"
  val akkaHttpVersion = "10.0.15"

  val compile = Seq(
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.fasterxml" % "aalto-xml" % "1.2.2"
  )

  val test = Seq(
    "org.pegdown" % "pegdown" % "1.6.0" % "test",
    // needs to be 1.7.1 for scalatest dependency to match hmrctest's scalatest dependency
    "org.mockito" %% "mockito-scala-scalatest" % "1.7.1" % "test",
    "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.25" % "test",
    "uk.gov.hmrc" %% "hmrctest"  % "3.9.0-play-26" % "test"
  )

  def apply(): Seq[ModuleID] = compile ++ test
}