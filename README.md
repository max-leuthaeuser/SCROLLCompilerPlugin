SCROLL Compiler Plugin
======================

This project contains a Scala compiler plugin supporting [SCROLL][scroll].

**1. Edit, develop and build:**
  1. Clone this repo.
  2. You may want to use SBT and run ```gen-idea``` if you are using Intellij IDE (to config see [here][sbt-gen-idea]).
  3. You may want to use SBT and run ```eclipse``` if you are using the Eclipse Scala IDE (to config see [here][gen-eclipse]).
  4. Invoke ```sbt assembly```. The resulting ```SCROLLCompilerPlugin.jar``` is stored under ```target/scala-2.11/```.

**2. Use the plugin:**
  1. Using Eclipse/Intellij: add ```SCROLLCompilerPlugin-assembly-versionNumber.jar``` to your IDEs Scala compiler plugin config.
  2. Or if you want to use it on the console directly with ```scalac```: add the ```-Xplugin:SCROLLCompilerPlugin-assembly-versionNumber.jar``` switch.
  3. Place your ```application.conf``` and CROM model ([CROM][crom], [FRaMED][framed]) in the resource folder (like in ```test/```).

**3. Example:**

```scala
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

This will generate the following compile output:

```
[info] Running the SCROLLCompilerPlugin with settings:
[info]  compile-time-errors: false
[info]  model-file: Test.crom

[info] Model 'Test.crom' was loaded.
[info] The following fills relations are specified:
[info]  'SomePlayer' -> 'SomeRole'

[warn] Test.scala:30: applyDynamic as 'foo(String, Int)' detected on: 'SomePlayer'.
[warn]  For 'SomePlayer' the following dynamic extensions are specified in 'Test.crom':
[warn]  'SomePlayer' -> 'SomeRole'
[warn]         val _: String = c.foo("42", 1)
[warn]                         ^
[warn] Test.scala:42: applyDynamic as 'world' detected on: 'SomePlayer'.
[warn]  For 'SomePlayer' the following dynamic extensions are specified in 'Test.crom':
[warn]  'SomePlayer' -> 'SomeRole'
[warn]         val _: String = c.world()
[warn]                         ^
[warn] Test.scala:55: applyDynamic as 'NOworld' detected on: 'SomePlayer'.
[warn]  For 'SomePlayer' the following dynamic extensions are specified in 'Test.crom':
[warn]  'SomePlayer' -> 'SomeRole'
[warn]           val _: String = c.NOworld()
[warn]                           ^
[warn] Test.scala:55: Neither 'SomePlayer', nor its dynamic extensions specified in 'Test.crom' offer the called behavior!
[warn]  This may indicate a programming error!
[warn]           val _: String = c.NOworld()
[warn]                             ^
[warn] Test.scala:68: applyDynamicNamed as 'bla((String, String))' detected on: 'SomePlayer'.
[warn]  For 'SomePlayer' the following dynamic extensions are specified in 'Test.crom':
[warn]  'SomePlayer' -> 'SomeRole'
[warn]         val _: String = c.bla(param = "!")
[warn]                         ^
[warn] Test.scala:80: selectDynamic as 'value' detected on: 'SomePlayer'.
[warn]  For 'SomePlayer' the following dynamic extensions are specified in 'Test.crom':
[warn]  'SomePlayer' -> 'SomeRole'
[warn]         val _: Int = c.value
[warn]                      ^
[warn] Test.scala:92: updateDynamic as 'value' detected on: 'SomePlayer'.
[warn]  For 'SomePlayer' the following dynamic extensions are specified in 'Test.crom':
[warn]  'SomePlayer' -> 'SomeRole'
[warn]         c.value = 10
[warn]         ^

[warn] 7 warnings found
```
*(line numbers only for demonstration)*

[sbt-gen-idea]: https://github.com/mpeltonen/sbt-idea
[gen-eclipse]: https://github.com/typesafehub/sbteclipse
[scroll]: https://github.com/max-leuthaeuser/SCROLL
[crom]: https://github.com/Eden-06/CROM
[framed]: https://github.com/leondart/FRaMED
