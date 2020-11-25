import cats.effect._, org.http4s._, org.http4s.dsl.io._

import cats.implicits._
import org.http4s.server.blaze._
import org.http4s.implicits._
import org.http4s.server.Router
import cats.data.Kleisli
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

object FileHttpRoutes {
  def apply(
      blockingContext: ExecutionContext,
      copyThreadPoolContext: ExecutionContext
  )(implicit
      cs: ContextShift[IO],
      timer: Timer[IO],
      nonBlockingContext: ExecutionContext
  ): Kleisli[IO, Request[IO], Response[IO]] = {

    implicit val copyFileRequestDecoder = jsonOf[IO, CopyFileRequest]

    HttpRoutes
      .of[IO] {
        case req @ POST -> Root / "files" =>
          cs.evalOn(copyThreadPoolContext) {
            (for {
              copyFileRequest <- req.as[CopyFileRequest]
              orig <- IO(new File("temp/" + copyFileRequest.fileName))
              dest <- IO(new File("temp/route-distination.txt"))
              meta <- IO(new File("temp/route-distination.meta.json"))
              size <- FileHandler.copy(orig, dest, meta, 10)
              res <- Ok(SuccessResponse("ok").asJson.noSpaces)
            } yield res).handleErrorWith(error =>
              Ok(UnsuccessResponse(error.getMessage()).asJson.noSpaces)
            )
          }

        case req @ GET -> Root / "files" / fileName =>
          StaticFile
            .fromFile(
              new File("temp/" + fileName),
              blockingContext,
              Some(req)
            )
            .getOrElseF(NotFound()) // In case the file doesn't exis

        case GET -> Root / "files" / fileName / "meta" =>
          (for {
            meta <- IO(new File("temp/" + fileName))
            array <- FileHandler.readAll(meta)
            parsedJson <- IO {
              parse(array.map(_.toChar).mkString)
            }
            json <- parsedJson match {
              case Right(js) => IO(js)
              case Left(ex)  => IO(UnsuccessResponse(ex.getMessage()).asJson)
            }
            res <- Ok(SuccessResponse(json).asJson)
          } yield res).handleErrorWith(error => BadRequest(error.getMessage))
      }
      .orNotFound

  }

  case class CopyFileRequest(
      fileName: String
  )

  object CopyFileRequest {
    implicit val decoder = jsonOf[IO, CopyFileRequest]
  }

  case class SuccessResponse[A](
      data: A
  )

  case class UnsuccessResponse(
      error: String
  )

}
