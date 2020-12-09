import java.io._
import cats.effect._
import cats.syntax.all._
import scala.util.Try
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
import io.chrisdavenport.cats.effect.time.implicits._
import java.time.LocalDateTime
import scala.concurrent.duration.MILLISECONDS
import cats.syntax.apply
import scala.reflect.ClassTag
import cats.effect.Resource.Par.Tag
import cats.effect.{Blocker, ExitCode, IO, IOApp, Resource}
import fs2.{io, text, Stream}
import java.nio.file.Paths
import fs2.Pipe

trait Reader[F[_]] {
  def read: F[Array[Byte]]
}
trait Writer[F[_]] {
  def write(a: Array[Byte]): F[Unit]
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

final class FS2FileReaderImpl[F[_]: Sync: ContextShift](
    resource: Blocker,
    path: String
) extends FileReader[F] {
  def read: F[Array[Byte]] =
    readAll.compile.to[Array]

  def readAll: Stream[F, Byte] = {
    for {
      array <- io.file
        .readAll[F](Paths.get(path), resource.blockingContext, 4096)
    } yield array
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

object FS2FileReader {
  def apply[F[_]: Sync: ContextShift](path: String): Stream[F, FileReader[F]] =
    for {
      resource <- Stream.resource(Blocker[F])
      fileReader <- Stream.eval(
        Sync[F].delay(new FS2FileReaderImpl(resource, path))
      )
    } yield fileReader

}

trait FileWriter[F[_]] extends Writer[F]

final class FileWriterImpl[F[_]: Sync](resource: Resource[F, OutputStream])
    extends FileWriter[F] {
  def write(buffer: Array[Byte]): F[Unit] = resource.use { outputStream =>
    Sync[F].delay(outputStream.write(buffer, 0, buffer.size))
  }
}

final class FS2FileWriterImpl[F[_]: Sync: ContextShift](
    resource: Blocker,
    path: String
) extends FileWriter[F] {
  def write(a: Array[Byte]): F[Unit] =
    writeAll(a).compile.drain

  def writeAll(buffer: Array[Byte]): Stream[F, Unit] =
    for {
      res <- Stream
        .emits(buffer)
        .through(
          io.file.writeAll[F](Paths.get(path), resource.blockingContext)
        )
    } yield res
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

object FS2FileWriter {
  def apply[F[_]: Sync: ContextShift](path: String): Stream[F, FileWriter[F]] =
    for {
      resource <- Stream.resource(Blocker[F])
      fileReader <- Stream.eval(
        Sync[F].delay(new FS2FileWriterImpl(resource, path))
      )
    } yield fileReader
}

trait FileHandler[F[_]] {

  def copy(
      origin: File,
      destination: File,
      meta: File
  ): F[Long]

  def copy(
      origin: String,
      destination: String,
      meta: String
  ): F[Long]

  def read(
      file: File
  ): F[Array[Byte]]

  def read(
      path: String
  ): F[Array[Byte]]

  def write(
      file: File,
      buffer: Array[Byte]
  ): F[Unit]

  def write(
      path: String,
      buffer: Array[Byte]
  ): F[Unit]

}

object FileHandler {

  def apply[F[_]: Sync: Timer: ContextShift: Concurrent]: FileHandler[F] =
    new FileHandler[F] {

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

      def length[O]: Pipe[F, O, (O, Int)] =
        in =>
          for {
            len <- in.fold(0)((l, s) => l + 1)
            out <- in
          } yield (out, len)

      def copy(origin: String, destination: String, meta: String): F[Long] =
        Stream
          .resource(Blocker[F])
          .flatMap { blocker =>
            io.file
              .readAll[F](Paths.get(origin), blocker.blockingContext, 1)
              .through(length)
              .through{in => 
                in.map(_._1)
                .through(
                io.file
                  .writeAll(Paths.get(destination), blocker.blockingContext)
                ) >> in.map(_._2)
              }
              .through(in =>
                for {
                  len <- in
                  _ <- Stream.eval(Sync[F].delay(println("111111111111111111111111")))
                  _ <- Stream.eval(Sync[F].delay(println("111111111111111111111111")))
                  _ <- Stream.eval(Sync[F].delay(println("111111111111111111111111")))
                  _ <- Stream.eval(Sync[F].delay(println("111111111111111111111111")))
                  _ <- Stream.eval(Sync[F].delay(println("111111111111111111111111")))
                  _ <- Stream.eval(Sync[F].delay(println("111111111111111111111111")))
                  _ <- Stream.eval(Sync[F].delay(println("111111111111111111111111")))
                  _ <- Stream.eval(Sync[F].delay(println("111111111111111111111111")))
                  _ <- Stream.eval(Sync[F].delay(println("111111111111111111111111")))
                  _ <- Stream.eval(Sync[F].delay(println("111111111111111111111111")))
                  _ <- Stream.eval(Sync[F].delay(println("111111111111111111111111")))
                  _ <- Stream.eval(Sync[F].delay(println("111111111111111111111111")))
                  json <- Stream.eval(createMeta(origin, destination, len))
                  res <- Stream
                    .emits(json.toString().getBytes())
                    .through(
                      io.file
                        .writeAll(Paths.get(meta), blocker.blockingContext)
                    )
                } yield len.toLong
              )
          }
          .compile
          .last
          .map(_.getOrElse(1))
          

      def read(file: File): F[Array[Byte]] =
        for {
          fileReader <- Sync[F].delay(FileReader[F](file))
          bytes <- fileReader.read
        } yield bytes

      def read(path: String): F[Array[Byte]] = {
        val readAll = for {
          blocker <- Stream.resource(Blocker[F])
          fileReader <- FS2FileReader[F](path)
          res <- fileReader.asInstanceOf[FS2FileReaderImpl[F]].readAll
        } yield res
        readAll.compile.to[Array]
      }

      def write(file: File, buffer: Array[Byte]): F[Unit] =
        for {
          fileWriter <- Sync[F].delay(FileWriter[F](file))
          res <- fileWriter.write(buffer)
        } yield res

      def write(path: String, buffer: Array[Byte]): F[Unit] = {
        val writeAll = for {
          blocker <- Stream.resource(Blocker[F])
          fileWriter <- FS2FileWriter[F](path)
          res <- fileWriter.asInstanceOf[FS2FileWriterImpl[F]].writeAll(buffer)
        } yield res
        writeAll.compile.drain
      }

      def createMeta(
          file: File,
          origin: String,
          destination: String,
          size: Long
      ): F[Json] =
        for {
          su <- Sync[F].delay {
            if (size < Unit.Size.Kilo) (size, Unit.Symbol.Byte)
            else if (size < Unit.Size.Mega)
              (size / Unit.Size.Kilo, Unit.Symbol.Kilo)
            else if (size < Unit.Size.Giga)
              (size / Unit.Size.Mega, Unit.Symbol.Mega)
            else (size / Unit.Size.Giga, Unit.Symbol.Giga)
          }
          dateTime <- Clock[F].monotonic(MILLISECONDS)
          res <- Sync[F].delay(
            Meta(
              origin,
              destination,
              Volume(su._1, su._2),
              dateTime
            ).asJson
          )
        } yield res

      def createMeta(
          origin: String,
          destination: String,
          size: Long
      ): F[Json] =
        for {
          su <- Sync[F].delay {
            if (size < Unit.Size.Kilo) (size, Unit.Symbol.Byte)
            else if (size < Unit.Size.Mega)
              (size / Unit.Size.Kilo, Unit.Symbol.Kilo)
            else if (size < Unit.Size.Giga)
              (size / Unit.Size.Mega, Unit.Symbol.Mega)
            else (size / Unit.Size.Giga, Unit.Symbol.Giga)
          }
          dateTime <- Clock[F].realTime(MILLISECONDS)
          res <- Sync[F].delay(
            Meta(
              origin,
              destination,
              Volume(su._1, su._2),
              dateTime
            ).asJson
          )
        } yield res

      def transmit[F[_]: Sync](
          fileReader: FileReader[F],
          fileWriter: FileWriter[F],
          buffer: Array[Byte]
      ): F[Long] =
        for {
          bytes <- fileReader.read
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
    createAt: Long
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
