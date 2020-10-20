resolvers ++= Seq(
  "Narrative Releases plugins" at "s3://s3.amazonaws.com/narrative-artifact-releases",
  Resolver.url(
    "sbts3 ivy resolver plugins",
    url("https://dl.bintray.com/emersonloureiro/sbt-plugins")
  )(Resolver.ivyStylePatterns)
)

addSbtPlugin("io.narrative" % "common-build" % "3.0.7")
