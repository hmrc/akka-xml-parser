import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings

val appName = "akka-xml-parser"

ThisBuild / majorVersion := 2
ThisBuild / scalaVersion := "2.13.12"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning)
  .settings(DefaultBuildSettings.scalaSettings)
  .settings(DefaultBuildSettings.defaultSettings())
  .settings(
    libraryDependencies ++= AppDependencies(),
    scoverageSettings
  )

lazy val scoverageSettings = {
  Seq(
    ScoverageKeys.coverageExcludedPackages := "<empty>;.*BuildInfo*.",
    ScoverageKeys.coverageMinimumStmtTotal := 1,
    ScoverageKeys.coverageFailOnMinimum := false,
    ScoverageKeys.coverageHighlighting := true,
    Test / parallelExecution := false
  )
}
