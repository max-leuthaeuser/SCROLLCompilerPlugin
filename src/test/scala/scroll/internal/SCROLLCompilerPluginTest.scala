package scroll.internal

import org.scalatest.{Matchers, WordSpec}

class SCROLLCompilerPluginTest extends WordSpec with Matchers {

  case class SomePlayer(name: String) {
    def hello(): String = "Hello"
  }

  case class SomeRole(name: String) {
    val value: Int = 0

    def world(): String = "World"

    def foo(param1: String, param2: Int): String = world() + param1 + param2

    def bla(param: String): String = world() + param
  }

  "the plugin" should {
    "detect applyDynamic with arg" in {
      val _ = new Compartment {

        val p = SomePlayer("p")
        val r = SomeRole("r")

        val c = p play r

        val _: String = c.foo("42", 1)
      }
    }

    "detect applyDynamic" in {
      val _ = new Compartment {

        val p = SomePlayer("p")
        val r = SomeRole("r")

        val c = p play r

        val _: String = c.world()
      }
    }

    "detect applyDynamic and detect non-existing behavior" in {
      val _ = new Compartment {

        val p = SomePlayer("p")
        val r = SomeRole("r")

        val c = p play r

        an[RuntimeException] should be thrownBy {
          val _: String = c.NOworld()
        }
      }
    }

    "detect applyDynamicNamed" in {
      val _ = new Compartment {

        val p = SomePlayer("p")
        val r = SomeRole("r")

        val c = p play r

        val _: String = c.bla(param = "!")
      }
    }

    "detect selectDynamic" in {
      val _ = new Compartment {

        val p = SomePlayer("p")
        val r = SomeRole("r")

        val c = p play r

        val _: Int = c.value
      }
    }

    "detect updateDynamic" in {
      val _ = new Compartment {

        val p = SomePlayer("p")
        val r = SomeRole("r")

        val c = p play r

        c.value = 10
      }
    }

    "detect transfer to" in {
      val _ = new Compartment {

        val p1 = SomePlayer("p1")
        val p2 = SomePlayer("p2")
        val r = SomeRole("r")

        p1 play r

        p1 transfer r to p2

        val _: String = +p2 hello()
      }
    }

    "detect drop" in {
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

    "handle unkown dynamic extension" in {
      val _ = new Compartment {

        case class UnkownPlayer()

        val p = UnkownPlayer()
        val r = SomeRole("r")

        val c = p play r

        //an[RuntimeException] should be thrownBy {
        val _: String = c.bla("param")
        //}
      }
    }
  }
}
