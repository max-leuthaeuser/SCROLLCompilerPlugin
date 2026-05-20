SCROLL Compiler Plugin
======================

This project contains a Scala 3 compiler plugin supporting [SCROLL][scroll] 3.4.

**1. Edit, develop and build:**
  1. Clone this repo.
  2. IntelliJ IDEA: use the built-in import SBT project functionality (Scala **3.8.3**, JDK **17+**).
  3. Invoke `sbt assembly`. The resulting assembly JAR is stored under `target/scala-3.8.3/`.

**2. Use the plugin:**
  1. Using IntelliJ: add `SCROLLCompilerPlugin-assembly-<version>.jar` to your IDE's Scala compiler plugin configuration.
  2. Or on the console with `scalac`: add the `-Xplugin:SCROLLCompilerPlugin-assembly-<version>.jar` switch.
  3. Place your `application.conf` and CROM model ([CROM][crom], [FRaMED][framed]) on the classpath (see `src/test/resources/`).

The plugin is registered via `plugin.properties` (`pluginClass=scroll.internal.SCROLLCompilerPlugin`).

**3. Example:**

```scala
import scroll.*

case class SomePlayer() {
  def hello(): String = "Hello"
}

case class SomeRole() {
  val value: Int = 0
  def world(): String = "World"
  def foo(param1: String, param2: Int): String = world() + param1 + param2
  def bla(param: String): String = world() + param
}

new Compartment {
  val p = SomePlayer()
  val r = SomeRole()
  val c = p play r
  val _: String = c.foo("42", 1)
}

new Compartment {
  val p = SomePlayer()
  val r = SomeRole()
  val c = p play r
  val _: String = c.world()
}

new Compartment {
  val p = SomePlayer()
  val r = SomeRole()
  val c = p play r
  val _: String = c.NOworld()
}

new Compartment {
  val p = SomePlayer()
  val r = SomeRole()
  val c = p play r
  val _: String = c.bla(param = "!")
}

new Compartment {
  val p = SomePlayer()
  val r = SomeRole()
  val c = p play r
  val _: Int = c.value
}

new Compartment {
  val p = SomePlayer()
  val r = SomeRole()
  val c = p play r
  c.value = 10
}
```

When compiled with the plugin and `application.conf` pointing at a matching CROM (see `src/test/resources/Test.crom`), this emits **seven** warnings for dynamic calls on `SomePlayer` (including the missing `NOworld` behavior). The test `SCROLLCompilerPluginTest` ("matches the README example") compiles the same program; run `sbt test` and inspect compiler output.

Example warning shape:

```
[info] Running the SCROLLCompilerPlugin with settings:
[info]  compile-time-errors: false
[info]  model-file: Test.crom

[info] The following fills relations are specified:
[info]  - 'SomePlayer' -> 'SomeRole'

[warn] ... applyDynamic as 'foo(String, Int)' detected on: 'SomePlayer'.
[warn]  For 'SomePlayer' the following dynamic extensions are specified:
[warn]         - 'SomePlayer' -> 'SomeRole'
[warn]  Make sure at least one of the following dynamic extensions is bound:
...
[warn] 7 warnings found
```

## Publish to Maven Central

Releases are published to the [Sonatype Central Portal](https://central.sonatype.com/) using sbt's built-in support (sbt 1.11+) and [sbt-pgp](https://github.com/sbt/sbt-pgp). See the [sbt Sonatype guide](https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html) for background.

### Prerequisites

- A [Central Portal](https://central.sonatype.com/) account with access to the `com.github.max-leuthaeuser` namespace
- A [GPG key](https://central.sonatype.org/publish/requirements/gpg/) on your machine (`gpg` must be on your `PATH`)
- JDK 17+ and sbt 1.12+ (see `project/build.properties`)

### One-time credential setup

Generate a user token in the Central Portal, then store it locally (do not commit these files).

In `~/.sbt/1.0/credentials.sbt`:

```scala
credentials += Credentials(Path.userHome / ".sbt" / "sonatype_central_credentials")
```

In `~/.sbt/sonatype_central_credentials`:

```
host=central.sonatype.com
user=<your token username>
password=<your token password>
```

For CI, you can use the `SONATYPE_USERNAME` and `SONATYPE_PASSWORD` environment variables instead.

### Release steps

1. Set the release version in `build.sbt` (`ThisBuild / version`).
2. Stage signed artifacts to `target/sona-staging`:

   ```
   sbt publishSigned
   ```

3. Upload to the Central Portal:

   ```
   sbt sonaUpload
   ```

   Then open [central.sonatype.com](https://central.sonatype.com/) and publish the deployment from the UI.

   To upload and release in one step:

   ```
   sbt sonaRelease
   ```

Artifacts appear on Maven Central as `com.github.max-leuthaeuser:SCROLLCompilerPlugin_3:<version>` (and the `assembly` classifier JAR) after propagation.

[scroll]: https://github.com/max-leuthaeuser/SCROLL
[crom]: https://github.com/Eden-06/CROM
[framed]: https://github.com/leondart/FRaMED
