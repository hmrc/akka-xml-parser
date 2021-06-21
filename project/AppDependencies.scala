import sbt._

object AppDependencies {

  val akkaVersion = "2.5.26"

  val compile = Seq(
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.fasterxml" % "aalto-xml" % "1.0.0"
  )

  val test = Seq(
    "org.pegdown" % "pegdown" % "1.6.0" % "test",
    // needs to be 1.7.1 for scalatest dependency to match hmrctest's scalatest dependency
    "org.mockito" %% "mockito-scala-scalatest" % "1.7.1" % "test",
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % "test",
    "uk.gov.hmrc" %% "bootstrap-test-play-27" % "3.4.0" % "test, it",
    "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % "test"
  )

  def apply(): Seq[ModuleID] = compile ++ test
}
