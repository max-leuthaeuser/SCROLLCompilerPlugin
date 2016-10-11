package scroll.internal

import org.scalatest.{Matchers, WordSpec}

class SCROLLCompilerPluginTest extends WordSpec with Matchers {

  case class Account() {
    def hello(): String = "Hello"
  }

  case class Target() {
    val value: Int = 0

    def world(): String = "World"

    def foo(param1: String, param2: Int): String = world() + param1 + param2

    def bla(param: String): String = world() + param
  }

  "the plugin" should {
    "detect applyDynamic with arg" in {
      val _ = new Compartment {

        val p = Account()
        val r = Target()

        val c = p play r

        val _: String = c.foo("42", 1)
      }
    }

    "detect applyDynamic" in {
      val _ = new Compartment {

        val p = Account()
        val r = Target()

        val c = p play r

        val _: String = c.world()
      }
    }

    "detect applyDynamic and detect non-existing behavior" in {
      val _ = new Compartment {

        val p = Account()
        val r = Target()

        val c = p play r

        an[RuntimeException] should be thrownBy {
          val _: String = c.NOworld()
        }
      }
    }

    "detect applyDynamicNamed" in {
      val _ = new Compartment {

        val p = Account()
        val r = Target()

        val c = p play r

        val _: String = c.bla(param = "!")
      }
    }

    "detect selectDynamic" in {
      val _ = new Compartment {

        val p = Account()
        val r = Target()

        val c = p play r

        val _: Int = c.value
      }
    }

    "detect updateDynamic" in {
      val _ = new Compartment {

        val p = Account()
        val r = Target()

        val c = p play r

        c.value = 10
      }
    }
  }
}
