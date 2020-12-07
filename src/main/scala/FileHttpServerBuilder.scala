import cats.effect._, org.http4s._, org.http4s.dsl.io._
import cats.implicits._
import org.http4s.server.blaze._
import org.http4s.implicits._
import org.http4s.server.Router
import cats.data.Kleisli
import cats.effect.concurrent.{Semaphore, Ref}
import cats.effect.{Fiber, CancelToken}

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
import cats.instances.unit
import Main.AppConfig

trait FileHttpServerBuilder[F[_]] {
  def create(
      hostname: String,
      port: Int,
      httpApp: HttpApp[F]
  ): F[Boolean]
  def cancel(
      port: Int
  ): F[Boolean]
  def get: F[Map[Int, Fiber[F, Unit]]]
}

final class FileHttpServerBuilderImpl[F[
    _
]: ContextShift: Timer: Sync: ConcurrentEffect](
    servers: Ref[F, Map[Int, Fiber[F, Unit]]],
    openRequestNo: Int,
    executionContext: ExecutionContext
) extends FileHttpServerBuilder[F] {

  def create(hostname: String, port: Int, httpApp: HttpApp[F]): F[Boolean] =
    for {
      ioServers <- servers.get           
      result <- ioServers.get(port) match {
        case Some(fiber) => Sync[F].delay(false)
        case None =>
          for {
            fiber <- Concurrent[F].start(
              BlazeServerBuilder[F](executionContext)
                .bindHttp(port, hostname)
                .withHttpApp(httpApp)
                .serve
                .compile
                .drain
            )
            x <- servers.modify(list => (list.+(port -> fiber), list))
            _ <- Sync[F].delay(println(x)) 
            res <- Sync[F].delay(true)
          } yield res

      }
    } yield result
  def cancel(port: Int): F[Boolean] =
    for {
      ioServers <- servers.get
      ioServer <- Sync[F].delay(ioServers.get(port))
      canceled <- ioServer match {
        case Some(fiber) =>
          fiber.cancel >> servers
            .modify(list => (list.-(port), list)) >> Sync[F].delay(true)
        case None => Sync[F].delay(false)
      }
    } yield canceled
  def get: F[Map[Int, Fiber[F, Unit]]] =
    servers.get
}

object FileHttpServerBuilder {

  def apply[F[_]: Sync: ContextShift: Timer: ConcurrentEffect](
      servers: Ref[F, Map[Int, Fiber[F, Unit]]],
      config: AppConfig,
      executionContext: ExecutionContext
  ): F[FileHttpServerBuilder[F]] =
    Sync[F].delay {
      new FileHttpServerBuilderImpl[F](
        servers,
        config.openRequestNo,
        executionContext
      )
    }

}
