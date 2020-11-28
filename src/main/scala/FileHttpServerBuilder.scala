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
      servers: Ref[IO, Map[Int, Fiber[IO, Int]]],
      blockingContext: ExecutionContext
  )(implicit
      cs: ContextShift[IO],
      timer: Timer[IO],
      nonBlockingContext: ExecutionContext
  ): IO[Fiber[IO, Int]] =
    for {
      numOfRequests <- Ref.of[IO, Long](0)
      s <- servers.get
      server <- s.get(port) match {
        case Some(fiber) => IO(fiber)
        case None =>
          BlazeServerBuilder[IO](nonBlockingContext)
            .bindHttp(port, hostname)
            .withHttpApp(
              FileHttpRoutes(blockingContext, guard, numOfRequests, servers)
            )
            .serve
            .compile
            .drain
            .start
            .map(_.map(_ => port))
      }
      _ <- servers.modify(list => (list.+(port -> server), list))
    } yield server

}
