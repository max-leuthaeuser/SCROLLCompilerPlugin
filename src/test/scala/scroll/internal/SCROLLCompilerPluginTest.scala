package scroll.internal

import scroll.*
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class SCROLLCompilerPluginTest extends AnyFunSpec with Matchers {

  case class SomePlayer(name: String = "") {
    def hello(): String = "Hello"
  }

  case class SomeRole(name: String = "") {
    val value: Int = 0

    def world(): String = "World"

    def foo(param1: String, param2: Int): String = world() + param1 + param2

    def bla(param: String): String = world() + param
  }

  describe("the plugin") {
    it("detects applyDynamic with arg") {
      val _ = new Compartment {
        val p         = SomePlayer("p")
        val r         = SomeRole("r")
        val c         = p play r
        val _: String = c.foo("42", 1)
      }
    }

    it("detects applyDynamic") {
      val _ = new Compartment {
        val p         = SomePlayer("p")
        val r         = SomeRole("r")
        val c         = p play r
        val _: String = c.world()
      }
    }

    it("detects applyDynamic and detect non-existing behavior") {
      val _ = new Compartment {
        val p = SomePlayer("p")
        val r = SomeRole("r")
        val c = p play r
        an[RuntimeException] should be thrownBy {
          val _: String = c.NOworld()
        }
      }
    }

    it("detects applyDynamicNamed") {
      val _ = new Compartment {
        val p         = SomePlayer("p")
        val r         = SomeRole("r")
        val c         = p play r
        val _: String = c.bla(param = "!")
      }
    }

    it("detects selectDynamic") {
      val _ = new Compartment {
        val p      = SomePlayer("p")
        val r      = SomeRole("r")
        val c      = p play r
        val _: Int = c.value
      }
    }

    it("detects updateDynamic") {
      val _ = new Compartment {
        val p = SomePlayer("p")
        val r = SomeRole("r")
        val c = p play r
        c.value = 10
      }
    }

    it("detects transfer to") {
      val _ = new Compartment {
        val p1 = SomePlayer("p1")
        val p2 = SomePlayer("p2")
        val r  = SomeRole("r")
        p1 play r
        p1 transfer r `to` p2
        val _: String = p2.hello()
      }
    }

    it("detects drop") {
      val _ = new Compartment {
        val p = SomePlayer("p")
        val r = SomeRole("r")
        val c = p play r
        p drop r
        an[RuntimeException] should be thrownBy {
          val _: String = c.bla("param")
        }
      }
    }

    it("handle unknown player") {
      val _ = new Compartment {
        case class UnkownPlayer()

        val p         = UnkownPlayer()
        val r         = SomeRole("r")
        val c         = p play r
        val _: String = c.bla("param")
      }
    }

    it("matches the README example (seven dynamic-trait warnings at compile time)") {
      val _ = new Compartment {
        val p         = SomePlayer()
        val r         = SomeRole()
        val c         = p play r
        val _: String = c.foo("42", 1)
      }

      val _ = new Compartment {
        val p         = SomePlayer()
        val r         = SomeRole()
        val c         = p play r
        val _: String = c.world()
      }

      val _ = new Compartment {
        val p = SomePlayer()
        val r = SomeRole()
        val c = p play r
        an[RuntimeException] should be thrownBy {
          val _: String = c.NOworld()
        }
      }

      val _ = new Compartment {
        val p         = SomePlayer()
        val r         = SomeRole()
        val c         = p play r
        val _: String = c.bla(param = "!")
      }

      val _ = new Compartment {
        val p      = SomePlayer()
        val r      = SomeRole()
        val c      = p play r
        val _: Int = c.value
      }

      val _ = new Compartment {
        val p = SomePlayer()
        val r = SomeRole()
        val c = p play r
        c.value = 10
      }
    }

    it("handle unknown dynamic extension") {
      val _ = new Compartment {
        case class UnkownExtension()

        val p = SomePlayer("p")
        val r = UnkownExtension()
        val c = p play r
        an[RuntimeException] should be thrownBy {
          val _: String = c.bla("param")
        }
      }
    }
  }

}
