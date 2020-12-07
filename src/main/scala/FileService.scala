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
import cats.effect.concurrent.Semaphore

import cats.MonadError
import shapeless.ops.nat.Prod
import Main.AppConfig
import org.http4s.blazecore.util.EntityBodyWriter
import scala.util.Success
import cats.syntax.validated

import cats.data._
import cats.data.Validated._
import cats.implicits._

sealed trait Validator[F[_]] {
  def validateName(name: String): F[String]
}

object Validator {
  def apply[F[_]: Sync]: Validator[F] =
    new Validator[F] {
      def validateName(name: String): F[String] =
        Sync[F].fromValidated(
          Either
            .cond(
              // name.matches("^[a-zA-Z]+$"),
              true,
              name,
              new Exception("the name is not valid")
            )
            .toValidated
        )
    }
}

trait FileServiceResponse[F[_]] extends Product
case class SuccessResponse[F[_], A](data: A) extends FileServiceResponse[F]
case class UnsuccessResponse[F[_], A](error: A) extends FileServiceResponse[F]

trait FileService[F[_]] {
  def copy(origin: String, destination: String): F[FileServiceResponse[F]]
  def get(origin: String): F[FileServiceResponse[F]]
}

final class FileServiceImpl[F[_]: Sync: Timer: ContextShift]
    extends FileService[F] {
  def copy(origin: String, destination: String): F[FileServiceResponse[F]] =
    (for {
      validatedOrigin <- Validator[F].validateName(origin)
      validatedDestination <- Validator[F].validateName(destination)
      originFile <- Sync[F].delay(new File(validatedOrigin))
      destinationFile <- Sync[F].delay(new File(validatedDestination))
      metaFile <- Sync[F].delay(new File(destination + ".mata.json"))
      result <- FileHandler[F].copy(originFile, destinationFile, metaFile)
    } yield SuccessResponse[F, Long](result)
      .asInstanceOf[FileServiceResponse[F]])
      .handleError { error: Throwable =>
        UnsuccessResponse[F, String](error.getMessage)
      }

  def get(fileName: String): F[FileServiceResponse[F]] =
    (for {
      validatedFileName <- Validator[F].validateName(fileName)
      file <- Sync[F].delay(new File(validatedFileName))
      result <- FileHandler[F].read(file)
    } yield SuccessResponse[F, Array[Byte]](result)
      .asInstanceOf[FileServiceResponse[F]])
      .handleError { error: Throwable =>
        UnsuccessResponse[F, String](error.getMessage)
      }

}
object FileService {
  def apply[F[_]: Sync: Timer: Concurrent: ContextShift](
      config: AppConfig
  ): F[FileService[F]] =
    for {
      fileService <- Sync[F].delay(new FileServiceImpl)
    } yield fileService

}
