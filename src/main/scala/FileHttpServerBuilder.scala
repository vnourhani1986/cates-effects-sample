import cats.effect._, org.http4s._, org.http4s.dsl.io._
import cats.implicits._
import org.http4s.server.blaze._
import org.http4s.implicits._
import org.http4s.server.Router
import cats.data.Kleisli
import cats.effect.concurrent.{Semaphore, Ref}

import io.circe._
import io.circe.parser._
import io.circe.generic.auto._
import io.circe.syntax._

import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import java.io._
import java.util.concurrent._
import scala.concurrent.ExecutionContext

object FileHttpServerBuilder {

  def apply(
      hostname: String,
      port: Int,
      guard: Semaphore[IO],
      numOfRequests: Ref[IO, Long], 
      blockingContext: ExecutionContext
  )(implicit
      cs: ContextShift[IO],
      timer: Timer[IO],
      nonBlockingContext: ExecutionContext
  ): IO[Unit] =
    BlazeServerBuilder[IO](nonBlockingContext)
      .bindHttp(port, hostname)
      .withHttpApp(FileHttpRoutes(blockingContext, guard, numOfRequests))
      .serve
      .compile
      .drain

}
