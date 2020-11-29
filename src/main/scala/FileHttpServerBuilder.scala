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

  def create(
      hostname: String,
      port: Int,
      guard: Semaphore[IO],
      servers: Ref[IO, Map[Int, Fiber[IO, Int]]],
      blockingContext: ExecutionContext
  )(implicit
      cs: ContextShift[IO],
      timer: Timer[IO],
      nonBlockingContext: ExecutionContext
  ): IO[Boolean] =
    for {
      numOfRequests <- Ref.of[IO, Long](0)
      ioServers <- servers.get
      httpRoutes <- IO {
        for {
          response <- FileHttpRoutes(blockingContext, guard, servers)
          limitedRoutes <- Kleisli { _: Any =>
            for {
              _ <- numOfRequests.modify(value => (value + 1, value + 1))
              res <- IO(response)
            } yield res
          }
        } yield limitedRoutes

      }
      result <- ioServers.get(port) match {
        case Some(fiber) => IO(false)
        case None =>
          for {
            fiber <- BlazeServerBuilder[IO](nonBlockingContext)
              .bindHttp(port, hostname)
              .withHttpApp(httpRoutes)
              .serve
              .compile
              .drain
              .start
              .map(_.map(_ => port))
            _ <- servers.modify(list => (list.+(port -> fiber), list))
            res <- IO(true)
          } yield res

      }
    } yield result

  def cancel(
      port: Int,
      servers: Ref[IO, Map[Int, Fiber[IO, Int]]]
  ): IO[Boolean] =
    for {
      ioServers <- servers.get
      ioServer <- IO(ioServers.get(port))
      canceled <- ioServer match {
        case Some(fiber) =>
          IO(fiber.cancel) >> servers.modify(list => (list.-(port), list)) >> IO
            .pure(true)
        case None => IO.pure(false)
      }
    } yield canceled

  def get(
      servers: Ref[IO, Map[Int, Fiber[IO, Int]]]
  ): IO[Map[Int, Fiber[IO, Int]]] =
    servers.get

}
