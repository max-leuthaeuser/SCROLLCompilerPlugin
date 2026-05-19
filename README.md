SCROLL Compiler Plugin
======================

This project contains a Scala compiler plugin supporting [SCROLL][scroll].

**1. Edit, develop and build:**
  1. Clone this repo.
  2. Use JDK 17+.
  3. Intellij IDE: use the built-in import SBT project functionality.
  4. Invoke ```sbt package```. The resulting plugin jar is stored under ```target/scala-3.8.3/```.

**2. Use the plugin:**
  1. Add the packaged jar to your Scala 3 compiler options via ```-Xplugin:/path/to/SCROLLCompilerPlugin_3-0.1.0.jar```.
  2. The current Scala 3 rewrite focuses on surfacing dynamic-dispatch call sites (`applyDynamic`, `applyDynamicNamed`, `selectDynamic`, `updateDynamic`) for SCROLL code.
  3. Use it together with SCROLL 3.4 and Scala 3.x projects.

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
[info]  - 'SomePlayer' -> 'SomeRole'

[warn] Test.scala:30: applyDynamic as 'foo(String, Int)' detected on: 'SomePlayer'.
[warn]  For 'SomePlayer' the following dynamic extensions are specified in 'Test.crom':
[warn]         - 'SomePlayer' -> 'SomeRole'
[warn]  Make sure at least one of the following dynamic extensions is bound:
[warn]         - 'SomeRole' maybe bound at:
[warn]                  [line:28|col:17] at source '.../Test.scala'
[warn]                  ...
[warn]         val _: String = c.foo("42", 1)
[warn]                         ^
[warn] Test.scala:42: applyDynamic as 'world' detected on: 'SomePlayer'.
[warn]  For 'SomePlayer' the following dynamic extensions are specified in 'Test.crom':
[warn]         - 'SomePlayer' -> 'SomeRole'
[warn]  Make sure at least one of the following dynamic extensions is bound:
[warn]         - 'SomeRole' maybe bound at:
[warn]                  [line:28|col:17] at source '.../Test.scala'
[warn]                  ...
[warn]         val _: String = c.world()
[warn]                         ^
[warn] Test.scala:55: applyDynamic as 'NOworld' detected on: 'SomePlayer'.
[warn]  For 'SomePlayer' the following dynamic extensions are specified in 'Test.crom':
[warn]          - 'SomePlayer' -> 'SomeRole'
[warn]           val _: String = c.NOworld()
[warn]                           ^
[warn] Test.scala:55: Neither 'SomePlayer', nor its dynamic extensions specified in 'Test.crom' offer the called behavior!
[warn]  This may indicate a programming error!
[warn]           val _: String = c.NOworld()
[warn]                             ^
[warn] Test.scala:68: applyDynamicNamed as 'bla((String, String))' detected on: 'SomePlayer'.
[warn]  For 'SomePlayer' the following dynamic extensions are specified in 'Test.crom':
[warn]         - 'SomePlayer' -> 'SomeRole'
[warn]  Make sure at least one of the following dynamic extensions is bound:
[warn]         - 'SomeRole' maybe bound at:
[warn]                  [line:28|col:17] at source '.../Test.scala'
[warn]                  ...
[warn]         val _: String = c.bla(param = "!")
[warn]                         ^
[warn] Test.scala:80: selectDynamic as 'value' detected on: 'SomePlayer'.
[warn]  For 'SomePlayer' the following dynamic extensions are specified in 'Test.crom':
[warn]         - 'SomePlayer' -> 'SomeRole'
[warn]  Make sure at least one of the following dynamic extensions is bound:
[warn]         - 'SomeRole' maybe bound at:
[warn]                  [line:28|col:17] at source '.../Test.scala'
[warn]                  ...
[warn]         val _: Int = c.value
[warn]                      ^
[warn] Test.scala:92: updateDynamic as 'value' detected on: 'SomePlayer'.
[warn]  For 'SomePlayer' the following dynamic extensions are specified in 'Test.crom':
[warn]         - 'SomePlayer' -> 'SomeRole'
[warn]  Make sure at least one of the following dynamic extensions is bound:
[warn]         - 'SomeRole' maybe bound at:
[warn]                  [line:28|col:17] at source '.../Test.scala'
[warn]                  ...
[warn]         c.value = 10
[warn]         ^

[warn] 7 warnings found
```
*(line numbers only for demonstration)*

[scroll]: https://github.com/max-leuthaeuser/SCROLL
[crom]: https://github.com/Eden-06/CROM
[framed]: https://github.com/leondart/FRaMED
