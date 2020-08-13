import sbt._

object AppDependencies {

  import play.core.PlayVersion
  import play.sbt.PlayImport._

  val compile = Seq(
    ws,
    "com.fasterxml" % "aalto-xml" % "1.0.0"
  )

  val test = Seq(
    "com.typesafe.play" %% "play-test" % PlayVersion.current % "test",
    "org.pegdown" % "pegdown" % "1.6.0" % "test",
    // needs to be 1.7.1 for scalatest dependency to match hmrctest's scalatest dependency
    "org.mockito" %% "mockito-scala-scalatest" % "1.7.1" % "test",
    "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.25" % "test",
    "uk.gov.hmrc" %% "hmrctest"  % "3.9.0-play-26" % "test"
  )

  def apply(): Seq[ModuleID] = compile ++ test
}
