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
    case class Account() {
        def hello(): String = "Hello"
    }

    case class Target() {
        val value: Int = 0
        def world(): String = "World"
        def bla(param: String): String = world() + param
    }

    new Compartment {
        val p = Account()
        val r = Target()
        val c = p play r
        val _: String = c.world()
    }

    new Compartment {
        val p = Account()
        val r = Target()
        val c = p play r
        val _: String = c.NOworld()
    }
```

This will generate the following compile output:

```
Information:scalac: Running the SCROLLCompilerPlugin with settings:
	compile-time-errors: false
	model-file: Bank.crom

Information:scalac: Model 'Bank.crom' was loaded.

Warning: applyDynamic detected on: Account.
	For 'Account' the following dynamic extensions are specified in 'Bank.crom':
	Target, Source, SavingsAccount, CheckingAccount
        val _: String = c.world()

Warning: applyDynamic detected on: Account.
	For 'Account' the following dynamic extensions are specified in 'Bank.crom':
	Target, Source, SavingsAccount, CheckingAccount
          val _: String = c.NOworld()
Warning: Neither 'Account', nor its dynamic extensions specified in 'Bank.crom' offer the called behavior!
	This may indicate a programming error!
          val _: String = c.NOworld()
```

[sbt-gen-idea]: https://github.com/mpeltonen/sbt-idea
[gen-eclipse]: https://github.com/typesafehub/sbteclipse
[scroll]: https://github.com/max-leuthaeuser/SCROLL
[crom]: https://github.com/Eden-06/CROM
[framed]: https://github.com/leondart/FRaMED
