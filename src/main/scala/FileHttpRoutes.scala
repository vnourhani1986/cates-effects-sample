import cats.effect._, org.http4s._, org.http4s.dsl.io._

import cats.implicits._
import org.http4s.server.blaze._
import org.http4s.implicits._
import org.http4s.server.Router
import cats.data.Kleisli
import cats.effect.concurrent.Semaphore
import cats.effect.concurrent.Ref

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
      guard: Semaphore[IO],
      servers: Ref[IO, Map[Int, Fiber[IO, Int]]]
  )(implicit
      cs: ContextShift[IO],
      timer: Timer[IO],
      nonBlockingContext: ExecutionContext
  ): Kleisli[IO, Request[IO], Response[IO]] = {

    implicit val copyFileRequestDecoder = jsonOf[IO, CopyFileRequest]
    implicit val spawnServerRequestDecoder = jsonOf[IO, SpawnServerRequest]

    HttpRoutes
      .of[IO] {
        case req @ POST -> Root / "files" =>
          guard.withPermit {
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

        case req @ POST -> Root / "spawn" =>
          (for {
            request <- req.as[SpawnServerRequest]
            server <- FileHttpServerBuilder
              .create(
                "localhost",
                request.port,
                guard,
                servers,
                blockingContext
              )
            res <- Ok(SuccessResponse("server created successfully").asJson)

          } yield res).handleErrorWith(error => BadRequest(error.getMessage))

        case GET -> Root / "servers" =>
          FileHttpServerBuilder
            .get(servers)
            .flatMap { ioServers =>
              Ok {
                SuccessResponse(
                  ioServers.map(s => s"host: localhost, port: ${s._1}")
                ).asJson
              }
            }
            .handleErrorWith(error => BadRequest(error.getMessage))

        case DELETE -> Root / "servers" / IntVar(port) =>
          FileHttpServerBuilder
            .cancel(
              port,
              servers
            )
            .flatMap {
              case true =>
                Ok(SuccessResponse("server canceled successfully").asJson)
              case false => BadRequest(UnsuccessResponse("server does not exists").asJson)
            }
            .handleErrorWith(error => BadRequest(error.getMessage))

      }
      .orNotFound

  }

  case class CopyFileRequest(
      fileName: String
  )

  object CopyFileRequest {
    implicit val decoder = jsonOf[IO, CopyFileRequest]
  }

  case class SpawnServerRequest(
      port: Int
  )

  object SpawnServerRequest {
    implicit val decoder = jsonOf[IO, SpawnServerRequest]
  }

  case class SuccessResponse[A](
      data: A
  )

  case class UnsuccessResponse(
      error: String
  )

}
