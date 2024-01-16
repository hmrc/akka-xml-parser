resolvers += Resolver.url("HMRC-open-artefacts-ivy", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns)
resolvers += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/"
resolvers += "HMRC-open-artefacts-maven" at "https://open.artefacts.tax.service.gov.uk/maven2"

addSbtPlugin("uk.gov.hmrc"    % "sbt-auto-build"      % "3.19.0")
addSbtPlugin("org.scoverage"  % "sbt-scoverage"       % "2.0.9")
