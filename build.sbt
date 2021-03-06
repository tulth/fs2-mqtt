import sbt.Keys._
import sbt._

lazy val IntegrationTest = config("it").extend(Test)

val filterConsoleScalacOptions = { options: Seq[String] =>
  options.filterNot(
    Set(
      "-Xfatal-warnings",
      "-Werror",
      "-Wdead-code",
      "-Wunused:imports",
      "-Ywarn-unused:imports",
      "-Ywarn-unused-import",
      "-Ywarn-dead-code"
    )
  )
}

lazy val commonSettings = Seq(
  organization := "net.sigusr",
  scalaVersion := "2.13.2",
  scalacOptions := Seq(
    "-encoding",
    "utf-8",
    "-explaintypes",
    "-feature",
    "-language:existentials",
    "-language:experimental.macros",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-Ymacro-annotations",
    "-Xfatal-warnings",
    "-unchecked",
    "-Xcheckinit",
    "-Xlint:adapted-args",
    "-Xlint:constant",
    "-Xlint:delayedinit-select",
    "-Xlint:deprecation",
    "-Xlint:doc-detached",
    "-Xlint:inaccessible",
    "-Xlint:infer-any",
    "-Xlint:missing-interpolator",
    "-Xlint:nullary-override",
    "-Xlint:nullary-unit",
    "-Xlint:option-implicit",
    "-Xlint:package-object-classes",
    "-Xlint:poly-implicit-overload",
    "-Xlint:private-shadow",
    "-Xlint:stars-align",
    "-Xlint:type-parameter-shadow",
    "-Wdead-code",
    "-Wextra-implicit",
    "-Wnumeric-widen",
    "-Wunused:implicits",
    "-Wunused:imports",
    "-Wunused:locals",
    "-Wunused:params",
    "-Wunused:patvars",
    "-Wunused:privates",
    "-Wvalue-discard"
  ),
  scalacOptions in (Compile, console) ~= filterConsoleScalacOptions,
  scalacOptions in Test ++= Seq("-Yrangepos"),
  scalacOptions in (Test, console) ~= filterConsoleScalacOptions
)

lazy val root = (project in file("."))
  .aggregate(core, examples)
  .settings(commonSettings: _*)

lazy val core = project
  .in(file("core"))
  .configs(IntegrationTest)
  .settings(
    commonSettings ++ testSettings ++ pgpSettings ++ publishingSettings ++ Seq(
      name := """fs2-mqtt""",
      version := "0.4.0",
      libraryDependencies ++= Seq(
        "com.beachape" %% "enumeratum" % "1.6.0",
        "org.specs2" %% "specs2-core" % "4.9.4" % "test",
        "org.scodec" %% "scodec-core" % "1.11.7",
        "org.scodec" %% "scodec-stream" % "2.0.0",
        "co.fs2" %% "fs2-core" % "2.4.0",
        "co.fs2" %% "fs2-io" % "2.4.0",
        "org.typelevel" %% "cats-core" % "2.1.1",
        "org.typelevel" %% "cats-effect" % "2.1.3",
        "com.github.cb372" %% "cats-retry" % "1.1.0",
        "com.github.julien-truffaut" %% "monocle-core" % "2.0.3",
        "com.github.julien-truffaut" %% "monocle-macro" % "2.0.3"
      )
    )
  )

lazy val examples = project
  .in(file("examples"))
  .dependsOn(core)
  .settings(
    commonSettings ++ Seq(
      libraryDependencies ++= Seq(
        "io.monix" %% "monix" % "3.2.1",
        "dev.zio" %% "zio-interop-cats" % "2.0.0.0-RC13"
      ),
      publish := ((): Unit),
      publishLocal := ((): Unit),
      publishArtifact := false
    )
  )

def itFilter(name: String): Boolean = name.startsWith("net.sigusr.mqtt.integration")
def unitFilter(name: String): Boolean = !itFilter(name)

def testSettings =
  Seq(
    testOptions in Test := Seq(Tests.Filter(unitFilter)),
    testOptions in IntegrationTest := Seq(Tests.Filter(itFilter))
  ) ++ inConfig(IntegrationTest)(Defaults.testTasks)

import com.jsuereth.sbtpgp.PgpKeys.{gpgCommand, pgpSecretRing, useGpg}

def pgpSettings =
  Seq(
    useGpg := true,
    gpgCommand := "/usr/bin/gpg",
    pgpSecretRing := file("~/.gnupg/secring.gpg")
  )

val ossSnapshots = "Sonatype OSS Snapshots".at("https://oss.sonatype.org/content/repositories/snapshots/")
val ossStaging = "Sonatype OSS Staging".at("https://oss.sonatype.org/service/local/staging/deploy/maven2/")

def projectUrl = "https://github.com/user-signal/fs2-mqtt"
def developerId = "fcabestre"
def developerName = "Frédéric Cabestre"
def licenseName = "Apache-2.0"
def licenseUrl = "http://opensource.org/licenses/Apache-2.0"
def licenseDistribution = "repo"
def scmUrl = projectUrl
def scmConnection = "scm:git:" + scmUrl

def generatePomExtra(scalaVersion: String): xml.NodeSeq =
  <url>
    {projectUrl}
  </url>
    <licenses>
      <license>
        <name>
          {licenseName}
        </name>
        <url>
          {licenseUrl}
        </url>
        <distribution>
          {licenseDistribution}
        </distribution>
      </license>
    </licenses>
    <scm>
      <url>
        {scmUrl}
      </url>
      <connection>
        {scmConnection}
      </connection>
    </scm>
    <developers>
      <developer>
        <id>
          {developerId}
        </id>
        <name>
          {developerName}
        </name>
      </developer>
    </developers>

def publishingSettings: Seq[Setting[_]] =
  Seq(
    credentialsSetting,
    publishMavenStyle := true,
    publishTo := version((v: String) => Some(if (v.trim.endsWith("SNAPSHOT")) ossSnapshots else ossStaging)).value,
    publishArtifact in Test := false,
    pomIncludeRepository := (_ => false),
    pomExtra := scalaVersion(generatePomExtra).value
  )

lazy val credentialsSetting = credentials += {
  Seq("SONATYPE_USER", "SONATYPE_PASS").map(k => sys.env.get(k)) match {
    case Seq(Some(user), Some(pass)) =>
      Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, pass)
    case _ =>
      Credentials(Path.userHome / ".ivy2" / ".credentials")
  }
}
