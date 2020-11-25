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
object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {

    for {
      nonBlockingPool <- IO(Executors.newFixedThreadPool(4))
      nonBlockingContext <- IO(ExecutionContext.fromExecutor(nonBlockingPool))
      blockingPool <- IO(Executors.newFixedThreadPool(4))
      blockingContext <- IO(
        Blocker
          .liftExecutorService(blockingPool)
          .blockingContext
      )
      x <- (
        FileHttpServerBuilder("localhost", 8080, blockingContext)(
          contextShift,
          timer,
          nonBlockingContext
        ),
        FileHttpServerBuilder("localhost", 8081, blockingContext)(
          contextShift,
          timer,
          nonBlockingContext
        ),
        FileHttpServerBuilder("localhost", 8082, blockingContext)(
          contextShift,
          timer,
          nonBlockingContext
        )
      ).parMapN((_, _, _) => ())

    } yield ExitCode.Success
  }
}
