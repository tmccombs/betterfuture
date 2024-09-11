import mill._
import scalalib._
import scalafmt._

object betterfuture extends RootModule with ScalaModule with ScalafmtModule {
  def scalaVersion = "3.5.0"

  object test extends ScalaTests with TestModule.ScalaTest {
    def ivyDeps = Agg(ivy"org.scalatest::scalatest:3.2.19")
  }
}
