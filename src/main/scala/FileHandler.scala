import java.io._
import cats.effect._
import cats.syntax.all._
import scala.util.Try
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
import io.chrisdavenport.cats.effect.time.implicits._
import java.time.LocalDateTime
import cats.syntax.apply
import scala.reflect.ClassTag
import cats.effect.Resource.Par.Tag
trait Reader[F[_]] {
  def read[A]: F[A]
}
trait Writer[F[_]] {
  def write[A](a: A): F[Unit]
}

trait FileReader[F[_]] extends Reader[F]

final class FileReaderImpl[F[_]: Sync](resource: Resource[F, InputStream])
    extends FileReader[F] {
  def read: F[Array[Byte]] = resource.use { inputStream =>
    for {
      buffer <- Sync[F].delay(Array[Byte](1))
      amount <- Sync[F].delay(inputStream.read(buffer, 0, 1))
      bytes <-
        if (amount > 0)
          read.map(buffer ++ _)
        else Sync[F].delay(Array.empty[Byte])
    } yield bytes
  }
}
object FileReader {
  def apply[F[_]: Sync](file: File): FileReader[F] =
    new FileReaderImpl(Resource.make {
      Sync[F].delay(new FileInputStream((file)))
    } { inStream =>
      Sync[F]
        .delay(inStream.close)
    })
}

trait FileWriter[F[_]] extends Writer[F]

final class FileWriterImpl[F[_]: Sync](resource: Resource[F, OutputStream])
    extends FileWriter[F] {
  def write(buffer: Array[Byte]): F[Unit] = resource.use { outputStream =>
    Sync[F].delay(outputStream.write(buffer, 0, buffer.size))
  }
}

object FileWriter {
  def apply[F[_]: Sync](file: File): FileWriter[F] =
    new FileWriterImpl(Resource.make {
      Sync[F].delay(new FileOutputStream(file))
    } { outStream =>
      Sync[F]
        .delay(outStream.close)
    })
}

trait FileHandler[F[_]] {

  def copy(
      origin: File,
      destination: File,
      meta: File
  ): F[Long]

  def read(
      file: File
  ): F[Array[Byte]]

  def write(
      file: File,
      buffer: Array[Byte]
  ): F[Unit]

}

object FileHandler {

  def apply[F[_]: Sync: Timer]: FileHandler[F] = new FileHandler[F] {

    def copy(
        origin: File,
        destination: File,
        meta: File
    ): F[Long] =
      for {
        fileReader <- Sync[F].delay(FileReader[F](origin))
        fileWriter <- Sync[F].delay(FileWriter[F](destination))
        size <- transfer(fileReader, fileWriter)
        _ <- createMeta(meta, origin.getPath, destination.getPath, size)
      } yield size

    def read(file: File): F[Array[Byte]] =
      for {
        fileReader <- Sync[F].delay(FileReader[F](file))
        bytes <- fileReader.read[Array[Byte]]
      } yield bytes

    def write(file: File, buffer: Array[Byte]): F[Unit] =
      for {
        fileWriter <- Sync[F].delay(FileWriter[F](file))
        res <- fileWriter.write(buffer)
      } yield res

    def createMeta(
        file: File,
        origin: String,
        destination: String,
        size: Long
    ): F[Unit] =
      Sync[F].delay {
        if (size < Unit.Size.Kilo) (size, Unit.Symbol.Byte)
        else if (size < Unit.Size.Mega)
          (size / Unit.Size.Kilo, Unit.Symbol.Kilo)
        else if (size < Unit.Size.Giga)
          (size / Unit.Size.Mega, Unit.Symbol.Mega)
        else (size / Unit.Size.Giga, Unit.Symbol.Giga)
      }

    def transmit[F[_]: Sync](
        fileReader: FileReader[F],
        fileWriter: FileWriter[F],
        buffer: Array[Byte]
    ): F[Long] =
      for {
        bytes <- fileReader.read[Array[Byte]]
        _ <- fileWriter.write(bytes)
      } yield bytes.size

    def transfer(
        fileReader: FileReader[F],
        fileWriter: FileWriter[F]
    ): F[Long] =
      for {
        buffer <- Sync[F].delay(new Array[Byte](1))
        total <- transmit(fileReader, fileWriter, buffer)
      } yield total

  }
}
case class Meta(
    input: String,
    output: String,
    volume: Volume,
    createAt: LocalDateTime
)

case class Volume(
    size: Long,
    unit: String
)

object Unit {
  object Size {
    val Byte = 1L
    val Kilo = 1000L
    val Mega = 1000000L
    val Giga = 1000000000L
  }

  object Symbol {
    val Byte = "B"
    val Kilo = "K"
    val Mega = "M"
    val Giga = "G"
  }

}
