import java.io._
import cats.effect._
import cats.syntax.all._
import scala.util.Try
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
import cats.effect._
import cats.effect.concurrent.{Semaphore, Ref}

import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze._
import scala.concurrent.ExecutionContext.global

import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import java.io._
import java.util.concurrent._
import scala.concurrent.ExecutionContext

import pureconfig._
import pureconfig.generic.auto._
import pureconfig.module.catseffect.syntax._

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {

    for {
      nonBlockingPool <- IO(Executors.newFixedThreadPool(4))
      nonBlockingContext <- IO(ExecutionContext.fromExecutor(nonBlockingPool))
      blockingPool <- IO(Executors.newFixedThreadPool(4))
      blocker <- IO(
        Blocker
          .liftExecutorService(blockingPool)
      )
      blockingContext <- IO(blocker.blockingContext)
      config <- load(blocker)
      guard <- Semaphore[IO](config.openRequestNo)
      configList <- IO(config.hosts.zip(config.ports))
      servers <- Ref.of[IO, Map[Int, Fiber[IO, Int]]](
        Map.empty[Int, Fiber[IO, Int]]
      )
      _ <- configList.map { case (host, port) =>
        FileHttpServerBuilder(
          host,
          port,
          guard,
          servers,
          blockingContext
        )(
          contextShift,
          timer,
          nonBlockingContext
        )
      }.parSequence
    } yield ExitCode.Success
  }

  def load(blocker: Blocker): IO[AppConfig] = {
    ConfigSource
      .file("src/main/resources/application.conf")
      .loadF[IO, AppConfig](blocker)
  }

  case class AppConfig(
      hosts: List[String],
      ports: List[Int],
      openRequestNo: Int
  )

}
