import java.io._
import cats.effect._
import cats.syntax.all._
import scala.util.Try
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
import cats.effect._
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
      copyThreadPool <- IO(Executors.newFixedThreadPool(config.openRequestNo))
      copyThreadPoolContext <- IO(ExecutionContext.fromExecutor(copyThreadPool))    
      _ <- config.hosts.map { case Host(host, port) =>
        FileHttpServerBuilder(
          host,
          port,
          blockingContext,
          copyThreadPoolContext
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

  case class Host(
      hostname: String,
      port: Int
  )

  case class AppConfig(
      hosts: List[Host],
      openRequestNo: Int
  )

}
