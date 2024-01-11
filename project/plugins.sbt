resolvers += Resolver.url("HMRC-open-artefacts-ivy", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns)
resolvers += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/"
resolvers += "HMRC-open-artefacts-maven" at "https://open.artefacts.tax.service.gov.uk/maven2"

addSbtPlugin("uk.gov.hmrc"    % "sbt-auto-build"      % "3.18.0")
addSbtPlugin("uk.gov.hmrc"    % "sbt-git-versioning"  % "2.4.0")
addSbtPlugin("org.scoverage"  % "sbt-scoverage"       % "2.0.9")
addSbtPlugin("uk.gov.hmrc"    % "sbt-settings"        % "4.19.0")
