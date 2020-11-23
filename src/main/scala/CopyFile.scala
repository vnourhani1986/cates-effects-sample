import java.io._
import cats.effect._
import cats.syntax.all._
import scala.util.Try
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._

object CopyFile {
  def apply(
      origin: File,
      destination: File,
      meta: File,
      bufferSize: Int
  ): IO[Long] =
    for {
      size <- inputOutputStreams(origin, destination).use { case (in, out) =>
        transfer(in, out, bufferSize)
      }
      _ <- createMeta(meta, origin.getPath, destination.getPath, size)
    } yield size

  def writeMeta(outputStream: OutputStream, meta: Array[Byte]): IO[Unit] =
    IO(outputStream.write(meta, 0, meta.length))

  def createMeta(
      file: File,
      origin: String,
      destination: String,
      size: Long
  ): IO[Unit] =
    outputStream(file).use { out =>
      for {
        calc <- calculateUnit(size)
        (scaledSize, unit) = calc
        buffer <- IO(
          Meta(
            origin,
            destination,
            Volume(scaledSize, unit)
          ).asJson.noSpaces.getBytes
        )
        result <- writeMeta(out, buffer)
      } yield result
    }

  def calculateUnit(size: Long): IO[(Long, String)] =
    IO {
      if (size < Unit.Size.Kilo) (size, Unit.Symbol.Byte)
      else if (size < Unit.Size.Mega) (size / Unit.Size.Kilo, Unit.Symbol.Kilo)
      else if (size < Unit.Size.Giga) (size / Unit.Size.Mega, Unit.Symbol.Mega)
      else (size / Unit.Size.Giga, Unit.Symbol.Giga)
    }

  def transmit(
      origin: InputStream,
      destination: OutputStream,
      buffer: Array[Byte],
      acc: Long
  ): IO[Long] =
    for {
      amount <- IO(origin.read(buffer, 0, buffer.size))
      count <-
        if (amount > -1)
          IO(destination.write(buffer, 0, amount)) >> transmit(
            origin,
            destination,
            buffer,
            acc + amount
          )
        else IO.pure(acc)
    } yield count

  def transfer(
      origin: InputStream,
      destination: OutputStream,
      bufferSize: Int
  ): IO[Long] =
    for {
      buffer <- IO(new Array[Byte](bufferSize))
      total <- transmit(origin, destination, buffer, 0)
    } yield total

  def inputStream(f: File): Resource[IO, FileInputStream] =
    Resource.make {
      IO(new FileInputStream((f)))
    } { inStream =>
      IO(inStream.close).handleErrorWith(_ => IO(println("input file closed")))
    }

  def outputStream(f: File): Resource[IO, FileOutputStream] =
    Resource.make {
      IO(new FileOutputStream(f))
    } { outStream =>
      IO(outStream.close).handleErrorWith(_ =>
        IO(println("output file closed"))
      )
    }

  def inputOutputStreams(
      in: File,
      out: File
  ): Resource[IO, (InputStream, OutputStream)] =
    for {
      inStream <- inputStream(in)
      outStream <- outputStream(out)
    } yield (inStream, outStream)

}

case class Meta(
    input: String,
    output: String,
    volume: Volume
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

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    for {
      _ <-
        if (args.length < 3)
          IO.raiseError(
            new IllegalArgumentException(" need origin and destination files")
          )
        else IO.unit
      bufferSize <- Try {
        args(2).toInt
      }.toEither match {
        case Right(value) => IO.pure(value)
        case Left(ex)     => IO.raiseError(ex)
      }
      _ <-
        if (args(0) == args(1))
          IO.raiseError(
            new IllegalArgumentException("origin and destination are same")
          )
        else IO.unit
      orig <- IO(new File(args(0)))
      dest <- IO(new File(args(1)))
      meta <- IO(
        new File(args(1).substring(0, args(1).indexOf('.')) + ".meta.json")
      )
      count <- CopyFile(orig, dest, meta, bufferSize)
      _ <- IO(
        println(s"$count bytes copied from ${orig.getPath} to ${dest.getPath}")
      )
    } yield ExitCode.Success

}
