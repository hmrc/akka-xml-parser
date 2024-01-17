import sbt._

object AppDependencies {

  val akkaVersion = "2.5.26"

  val compile = Seq(
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.fasterxml"     % "aalto-xml"    % "1.0.0"
  )

  val test = Seq(
    "org.pegdown"       % "pegdown"                  % "1.6.0"     % "test",
    "org.mockito"       %% "mockito-scala-scalatest" % "1.7.1"    % "test",
    "com.typesafe.akka" %% "akka-stream-testkit"     % akkaVersion % "test",
    "org.scalatest"     %% "scalatest"               % "3.0.9"     % "test"
  )

  def apply(): Seq[ModuleID] = compile ++ test
}
