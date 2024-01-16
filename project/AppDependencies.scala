import sbt._

object AppDependencies {

  val pekkoVersion = "1.0.2"

  val compile = Seq(
    "org.apache.pekko" %% "pekko-stream" % pekkoVersion,
    "com.fasterxml"     % "aalto-xml"    % "1.0.0"
  )

  val test = Seq(
    "org.pegdown"             %  "pegdown"                  % "1.6.0"      % Test,
    "org.mockito"             %% "mockito-scala-scalatest"  % "1.17.12"    % Test,
    "org.apache.pekko"        %% "pekko-stream-testkit"     % pekkoVersion % Test,
    "org.scalatest"           %% "scalatest"                % "3.2.15"     % Test,
    "com.vladsch.flexmark"    %  "flexmark-all"             % "0.64.4"     % Test
  )

  def apply(): Seq[ModuleID] = compile ++ test
}
