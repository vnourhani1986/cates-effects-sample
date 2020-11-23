import java.io._
import cats.effect._
import cats.syntax.all._
import scala.util.Try


object CopyFile {
  def apply(origin: File, destination: File, bufferSize: Int): IO[Long] =
    inputOutputStreams(origin, destination).use { case (in, out) =>
      transfer(in, out, bufferSize)
    }

  def transmit(origin: InputStream, destination: OutputStream, buffer: Array[Byte], acc: Long): IO[Long] =
    for {
      amount <- IO(origin.read(buffer, 0, buffer.size))
      count <- if (amount > -1) IO(destination.write(buffer, 0, amount)) >> transmit(origin, destination, buffer, acc + amount)
      else IO.pure(acc)
    } yield count

  def transfer(origin: InputStream, destination: OutputStream, bufferSize: Int): IO[Long] =
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
      IO(outStream.close).handleErrorWith(_ => IO(println("output file closed")))
    }

  def inputOutputStreams(in: File, out: File): Resource[IO, (InputStream, OutputStream)] =
    for {
      inStream <- inputStream(in)
      outStream <- outputStream(out)
    } yield (inStream, outStream)

}


object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    for {
      _ <- if (args.length < 3) IO.raiseError(new IllegalArgumentException(" need origin and destination files"))
      else IO.unit
      bufferSize <- Try {
        args(2).toInt
      }.toOption match {
          case Some(value) => IO.pure(value)
          case None => IO.raiseError(new IllegalArgumentException("buffer size need to be int value"))
      }
      _ <- if (args(0) == args(1)) IO.raiseError(new IllegalArgumentException("origin and destination are same"))
      else IO.unit
      orig <- IO(new File(args(0)))
      dest <- IO(new File(args(1)))
      //      _ <- if (dest.exists()) Resource.make {
      //        IO( new Scanner(System.in))
      //      } { inScanner =>
      //        IO(inScanner).withErrorHandle(_ => IO(println("input scanner closed")))
      //      }
      //      else IO.unit
      count <- CopyFile(orig, dest, bufferSize)
      _ <- IO(println(s"$count bytes copied from ${orig.getPath} to ${dest.getPath}"))
    } yield ExitCode.Success
}