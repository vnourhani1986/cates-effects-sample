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
import HttpService._

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    for {
    //   _ <-
    //     if (args.length < 3)
    //       IO.raiseError(
    //         new IllegalArgumentException(" need origin and destination files")
    //       )
    //     else IO.unit
    //   bufferSize <- Try {
    //     args(2).toInt
    //   }.toEither match {
    //     case Right(value) => IO.pure(value)
    //     case Left(ex)     => IO.raiseError(ex)
    //   }
    //   _ <-
    //     if (args(0) == args(1))
    //       IO.raiseError(
    //         new IllegalArgumentException("origin and destination are same")
    //       )
    //     else IO.unit
    //   orig <- IO(new File(args(0)))
    //   dest <- IO(new File(args(1)))
    //   meta <- IO(
    //     new File(args(1).substring(0, args(1).indexOf('.')) + ".meta.json")
    //   )
    //   count <- CopyFile(orig, dest, meta, bufferSize)
    //   _ <- IO(
    //     println(s"$count bytes copied from ${orig.getPath} to ${dest.getPath}")
    //   )
      _ <- serviceBuilder
        .as(ExitCode.Success)
    } yield ExitCode.Success

}
