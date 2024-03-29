import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings

val appName = "akka-xml-parser"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning)
  .settings(majorVersion := 1)
  .settings(DefaultBuildSettings.scalaSettings)
  .settings(DefaultBuildSettings.defaultSettings())
  .settings(
    scalaVersion := "2.13.8",
    libraryDependencies ++= AppDependencies(),
    scoverageSettings
  )

lazy val scoverageSettings = {
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := "<empty>;.*BuildInfo*.",
    ScoverageKeys.coverageMinimum := 1,
    ScoverageKeys.coverageFailOnMinimum := false,
    ScoverageKeys.coverageHighlighting := true,
    parallelExecution in Test := false
  )
}
