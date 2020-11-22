import cats.effect._
import cats.syntax.all._
import java.io._


class CopyFile {
  def copy(origin: File, destination: File): IO[Long] =
    inputOutputStreams(origin, destination).use { case (in, out) =>
      transfer(in, out)
    }

  def transmit(origin: InputStream, destination: OutputStream, buffer: Array[Byte], acc: Long): IO[Long] =
    for {
      amount <- IO(origin.read(buffer, 0, buffer.size))
      count <- if (amount > -1) IO(destination.write(buffer, 0, amount)) >> transmit(origin, destination, buffer, acc + amount)
      else IO.pure(acc)
    } yield count

  def transfer(origin: InputStream, destination: OutputStream): IO[Long] =
    for {
      buffer <- IO(new Array[Byte](1024 * 10))
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
      _ <- if (args.length < 2) IO.raiseError(new IllegalArgumentException(" need origin and destination files"))
      else IO.unit
      orig = new File(args(0))
      dest = new File(args(1))
      count <- (new CopyFile).copy(orig, dest)
      _ <- IO(println(s"$count bytes copied from ${orig.getPath} to ${dest.getPath}"))
    } yield ExitCode.Success
}