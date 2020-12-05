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

// // algebra
// trait FileService[F[_], Req[_], Res[_]] {
//   def create(request: Request[F]): F[Response[F]]
//   def getFile(fileName: String): F[Response[F]]
//   def getMeta(fileName: String): F[Response[F]]
//   def copy(request: Request[F]): F[Response[F]]
//   def delete(request: Request[F]): F[Response[F]]
// }
// program
// final class FileService1Impl[F[_]: Sync: Timer: ContextShift](
//     guard: Semaphore[F],
//     servers: Ref[F, Map[Int, Fiber[F, Int]]]
// ) extends FileService[F] {

//   implicit val copyFileRequestDecoder = jsonOf[F, CopyFileRequest]
//   implicit val successResponseEncoder: Encoder[SuccessResponse[Json]] =
//     new Encoder[SuccessResponse[Json]] {
//       final def apply(successResponse: SuccessResponse[Json]): Json =
//         successResponse.asJson
//     }
//   implicit def successResponseEntityEncoder[F[_]: Applicative]
//       : EntityEncoder[F, SuccessResponse[Json]] =
//     jsonEncoderOf[F, SuccessResponse[Json]]

//   def create(request: RequestModel): F[ResponseModel] = ???

//   def getFile(
//       fileName: String
//   )(implicit
//       blockingContext: ExecutionContext
//   ): F[Response[F]] =
//     StaticFile
//       .fromFile(
//         new File("temp/" + fileName),
//         blockingContext,
//         None
//       )
//       .getOrElseF(
//         Sync[F].delay(Response[F](Status.BadRequest))
//       ) // In case the file doesn't exist

//   def getMeta(fileName: String): F[Response[F]] =
//     for {
//       meta <- Sync[F].delay(new File("temp/" + fileName))
//       array <- FileHandler[F].read(meta)
//       parsedJson <- Sync[F].delay {
//         parse(array.map(_.toChar).mkString)
//       }
//       json <- parsedJson match {
//         case Right(js) => Sync[F].delay(js)
//         case Left(ex) =>
//           Sync[F].delay(UnsuccessResponse(ex.getMessage()).asJson)
//       }
//       res <- Sync[F].delay(
//         Response[F](Status.Ok)
//           .withEntity(SuccessResponse[Json](json))
//       )
//     } yield res

//   def copy(request: Request[F]): F[Response[F]] =
//     guard.withPermit {
//       for {
//         copyFileRequest <- request.as[CopyFileRequest]
//         orig <- Sync[F].delay(new File("temp/" + copyFileRequest.fileName))
//         dest <- Sync[F].delay(new File("temp/route-distination.txt"))
//         meta <- Sync[F].delay(new File("temp/route-distination.meta.json"))
//         size <- FileHandler[F].copy(orig, dest, meta, 10)
//         res <- Sync[F].delay(SuccessResponse("file is copied"))
//         result <- Sync[F].delay(Response[F](Status.Ok))
//       } yield result
//     }

//   def delete(request: RequestModel): F[Response[F]] = ???

// }

// import Main.AppConfig
// object FileService1 {
//   def apply[F[_]: Concurrent: Timer: ContextShift](
//       config: AppConfig
//   ): F[FileService[F]] =
//     for {
//       guard <- Semaphore[F](config.openRequestNo)
//       servers <- Ref[F].of[Map[Int, Fiber[F, Int]]](Map.empty)
//     } yield new FileServiceImpl(guard, servers)
// }

// trait FileHttpServer[F[_]] {
//   def create(request: Request[F]): F[Response[F]]
//   def get(request: Request[F]): F[Response[F]]
//   def remove(request: Request[F]): F[Response[F]]
// }

// class FileHttpServerImpl[F[_]: Sync](
//     guard: Semaphore[F],
//     servers: Ref[F, Map[Int, Fiber[F, Int]]]
// ) extends FileHttpServer[F] {

//   implicit val spawnServerRequestDecoder = jsonOf[IO, SpawnServerRequest]

//   def create(request: Request[F]): F[Response[F]] =
//     for {
//       req <- request.as[SpawnServerRequest]
//       server <- FileHttpServerBuilder
//         .create(
//           "localhost",
//           request.port,
//           guard,
//           servers,
//           blockingContext
//         )
//       res <- Ok(SuccessResponse("server created successfully").asJson)

//     } yield res
//   def get(request: Request[F]): F[Response[F]] = ???
//   def remove(request: Request[F]): F[Response[F]] = ???
// }

// object FileHttpServer {
//   def apply[F[_]](
//       servers: Ref[F, Map[Int, Fiber[F, Int]]]
//   ): FileHttpServer[F] = new FileHttpServerImpl[F](servers)
// }

object FileHttpRoutes {
  def apply[F[_]: Sync](
      guard: Semaphore[F],
      servers: Ref[F, Map[Int, Fiber[F, Int]]],
      fileService: FileService[IO]
  )(implicit
      blockingContext: ExecutionContext,
      cs: ContextShift[IO],
      timer: Timer[IO],
      nonBlockingContext: ExecutionContext
  ): Kleisli[IO, Request[IO], Response[IO]] = {

    implicit val copyFileRequestDecoder = jsonOf[F, CopyFileRequest]
    implicit val sizeDecoder = jsonOf[F, Size]

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

    implicit val spawnServerRequestDecoder = jsonOf[IO, SpawnServerRequest]

    def createFile(file: String): IO[String] = IO.pure(file)

    def FileServiceRouter(fileService: FileService[F]) =
      HttpRoutes.of[F] {
        case req @ POST -> Root / "files" =>
          guard.withPermit {
            for {
              copyFileRequest <- req.as[CopyFileRequest]
              copyFileResponse <- fileService.copy(
                "temp/" + copyFileRequest.fileName,
                "temp/route-distination.txt"
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
                    UnsuccessResponse(error: String).asJson
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
                        UnsuccessResponse(ex.getMessage()).asJson
                      )
                  }
                case UnsuccessResponse(error: String) =>
                  Response[F](Status.BadRequest).withEntity(
                    UnsuccessResponse(error: String).asJson
                  )
              }
            }
          } yield result
      }

    HttpRoutes
      .of[IO] {
        

        //   case req @ POST -> Root / "spawn" =>
        //     (for {
        //       request <- req.as[SpawnServerRequest]
        //       server <- FileHttpServerBuilder
        //         .create(
        //           "localhost",
        //           request.port,
        //           guard,
        //           servers,
        //           blockingContext
        //         )
        //       res <- Ok(SuccessResponse("server created successfully").asJson)

        //     } yield res).handleErrorWith(error => BadRequest(error.getMessage))

        //   case GET -> Root / "servers" =>
        //     FileHttpServerBuilder
        //       .get(servers)
        //       .flatMap { ioServers =>
        //         Ok {
        //           SuccessResponse(
        //             ioServers.map(s => s"host: localhost, port: ${s._1}")
        //           ).asJson
        //         }
        //       }
        //       .handleErrorWith(error => BadRequest(error.getMessage))

        //   case DELETE -> Root / "servers" / IntVar(port) =>
        //     FileHttpServerBuilder
        //       .cancel(
        //         port,
        //         servers
        //       )
        //       .flatMap {
        //         case true =>
        //           Ok(SuccessResponse("server canceled successfully").asJson)
        //         case false =>
        //           BadRequest(UnsuccessResponse("server does not exists").asJson)
        //       }
        //       .handleErrorWith(error => BadRequest(error.getMessage))

        // }
      }
      .orNotFound

  }

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
