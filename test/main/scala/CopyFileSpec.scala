import cats.effect.IO
import cats.effect.testing.specs2.CatsIO
import org.specs2.mutable.Specification

// for some reason, only class works here; object will not be detected by sbt
class CopyFileSpec extends Specification with CatsIO {

  override val Timeout = 5.second

  "examples" should {
    "do the things" in IO {
      true must beTrue
    }
  }
}