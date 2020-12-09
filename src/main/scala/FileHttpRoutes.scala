import cats.effect._, org.http4s._, org.http4s.dsl.io._

import cats.implicits._
import org.http4s.server.blaze._
import org.http4s.implicits._
import org.http4s.server.Router
import cats.data.Kleisli
import cats.effect.concurrent.Semaphore
import cats.effect.concurrent.Ref
import cats.Applicative

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

import cats.MonadError
import shapeless.ops.nat.Prod
import Main.AppConfig
import org.http4s.blazecore.util.EntityBodyWriter
import scala.util.Success

object FileHttpRoutes {
  def apply[F[_]: Sync: Timer: ConcurrentEffect: ContextShift](
      guard: Semaphore[F],
      servers: Ref[F, Map[Int, Fiber[F, Unit]]],
      config: AppConfig,
      // blockingContext: ExecutionContext,
      nonBlockingContext: ExecutionContext
  ): F[HttpRoutes[F]] = {

    implicit val copyFileRequestDecoder = jsonOf[F, CopyFileRequest]
    implicit val spawnServerRequestDecoder = jsonOf[F, SpawnServerRequest]
    implicit def successResponseEncoder[A](implicit
        jec: Encoder[A]
    ): Encoder[SuccessResponse[F, A]] =
      new Encoder[SuccessResponse[F, A]] {
        final def apply(successResponse: SuccessResponse[F, A]): Json =
          successResponse.asJson
      }
    implicit def successResponseEntityEncoder[F[_]: Applicative, A](implicit
        jec: Encoder[A]
    ): EntityEncoder[F, SuccessResponse[F, A]] =
      jsonEncoderOf[F, SuccessResponse[F, A]]

    implicit def unsuccessResponseEncoder[A](implicit
        jec: Encoder[A]
    ): Encoder[UnsuccessResponse[F, A]] =
      new Encoder[UnsuccessResponse[F, A]] {
        final def apply(UnsuccessResponse: UnsuccessResponse[F, A]): Json =
          UnsuccessResponse.asJson
      }
    implicit def unsuccessResponseEntityEncoder[F[_]: Applicative, A](implicit
        jec: Encoder[A]
    ): EntityEncoder[F, UnsuccessResponse[F, A]] =
      jsonEncoderOf[F, UnsuccessResponse[F, A]]

    def fileServiceRouter(fileService: FileService[F]): HttpRoutes[F] =
      HttpRoutes.of[F] {
        case req @ POST -> Root / "files" =>
          guard.withPermit {
            for {
              copyFileRequest <- req.as[CopyFileRequest]
              copyFileResponse <- fileService.copy(
                "temp/" + copyFileRequest.fileName,
                "temp/destination.txt"
              )
              result <- Sync[F].delay {                
                copyFileResponse match {
                  case SuccessResponse(size: Long) =>
                    Response(
                      Status.Ok
                    ).withEntity(
                      SuccessResponse[F, Long](size)
                    )
                  case UnsuccessResponse(error: String) =>
                    Response(
                      Status.BadRequest
                    ).withEntity(
                      UnsuccessResponse[F, String](error)
                    )
                }
              }
            } yield result
          }

        case req @ GET -> Root / "files" / fileName =>
          for {
            getMetaResponse <- fileService.get("temp/" + fileName)
            result <- Sync[F].delay {
              getMetaResponse match {
                case SuccessResponse(data: Array[Byte]) =>
                  Response[F](Status.Ok).withEntity(data)
                case UnsuccessResponse(error: String) =>
                  Response[F](Status.BadRequest).withEntity(
                    UnsuccessResponse[F, String](error).asJson
                  )
              }
            }
          } yield result

        case GET -> Root / "files" / fileName / "meta" =>
          for {
            getMetaResponse <- fileService.get("temp/" + fileName)
            result <- Sync[F].delay {
              getMetaResponse match {
                case SuccessResponse(data: Array[Byte]) =>
                  parse(data.map(_.toChar).mkString) match {
                    case Right(js) => Response[F](Status.Ok).withEntity(js)
                    case Left(ex) =>
                      Response[F](Status.BadRequest).withEntity(
                        UnsuccessResponse[F, String](ex.getMessage()).asJson
                      )
                  }
                case UnsuccessResponse(error: String) =>
                  Response[F](Status.BadRequest).withEntity(
                    UnsuccessResponse[F, String](error).asJson
                  )
              }
            }
          } yield result
      }

    def fileHttpServiceBuilderRouter(
        fileHttpServiceBuilder: FileHttpServerBuilder[F],
        fileServiceApp: HttpApp[F]
    ): HttpRoutes[F] =
      HttpRoutes
        .of[F] {
          case GET -> Root / "health" =>
            Sync[F].delay {
              Response[F](Status.Ok).withEntity("server is healthy")
            }
          case req @ POST -> Root / "spawn" =>
            for {
              request <- req.as[SpawnServerRequest]              
              server <- fileHttpServiceBuilder
                .create(
                  "localhost",
                  request.port,
                  fileServiceApp
                )
              res <- Sync[F].delay(
                Response[F](Status.Ok)
                .withEntity(
                  SuccessResponse[F, String]("server created successfully").asJson
                )
              )
            } yield res

          case GET -> Root / "servers" =>
            fileHttpServiceBuilder.get
              .flatMap { ioServers =>
                Sync[F].delay {
                  Response[F](Status.Ok).withEntity(
                    SuccessResponse[F, String](
                      ioServers.map(s => s"host: localhost, port: ${s._1}").mkString(",")
                    ).asJson
                  )
                }
              }

          case DELETE -> Root / "servers" / IntVar(port) =>
            fileHttpServiceBuilder
              .cancel(
                port
              )
              .flatMap {
                case true =>
                  Sync[F].delay(
                    Response[F](Status.Ok).withEntity(
                      SuccessResponse[F, String]("server canceled successfully").asJson
                    )
                  )
                case false =>
                  Sync[F].delay(
                    Response[F](Status.BadRequest).withEntity(
                      UnsuccessResponse[F, String]("server does not exists").asJson
                    )
                  )
              }

        }

    for {
      fileService <- FileService[F](config)
      httpRoutes <- Sync[F].delay(fileServiceRouter(fileService))
      fileHttpServerBuilder <- FileHttpServerBuilder(
        servers,
        config,
        nonBlockingContext
      )
      httpServerBuilder <- Sync[F].delay(
        fileHttpServiceBuilderRouter(
          fileHttpServerBuilder,
          fileServiceRouter(fileService).orNotFound
        )
      )
    } yield httpServerBuilder <+> httpRoutes

  }

}

case class CopyFileRequest(
    fileName: String
)

case class SpawnServerRequest(
    port: Int
)
