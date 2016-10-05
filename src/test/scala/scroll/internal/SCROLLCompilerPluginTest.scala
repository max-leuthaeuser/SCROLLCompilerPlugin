package scroll.internal

import org.scalatest.{WordSpec, Matchers}

class SCROLLCompilerPluginTest extends WordSpec with Matchers {

  case class SomePlayer() {
    def hello(): String = "Hello"
  }

  case class SomeRole() {
    val value: Int = 0

    def world(): String = "World"

    def bla(param: String): String = world() + param
  }

  "the plugin" should {
    "detect applyDynamic" in {
      new Compartment {

        val p = SomePlayer()
        val r = SomeRole()

        val c = p play r

        val _: String = c.world()
      }
    }

    "detect applyDynamicNamed" in {
      new Compartment {

        val p = SomePlayer()
        val r = SomeRole()

        val c = p play r

        val _: String = c.bla(param = "!")
      }
    }

    "detect selectDynamic" in {
      new Compartment {

        val p = SomePlayer()
        val r = SomeRole()

        val c = p play r

        val _: Int = c.value
      }
    }

    "detect updateDynamic" in {
      new Compartment {

        val p = SomePlayer()
        val r = SomeRole()

        val c = p play r

        c.value = 10
      }
    }
  }
}
