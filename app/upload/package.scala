import java.nio.file.{ Files, Path }

import zio.blocking.Blocking
import zio.random.Random
import zio.stream.{ Stream, ZStream }
import zio.{ Has, Layer, UIO, ZIO, ZLayer }

package object upload {
  type Uploads = Has[Uploads.Service]

  object Uploads {
    trait Service {
      def stream(name: String): ZStream[Blocking, Throwable, Byte]
      def file(name: String): ZIO[Any, Nothing, Option[FileMeta]]
    }

    def handler(fileHandle: String => UIO[Option[FileMeta]]): ZIO[Any, Nothing, Uploads] =
      ZIO
        .succeed(new Service {
          override def stream(name: String): ZStream[Blocking, Throwable, Byte] =
            for {
              ref <- ZStream.fromEffectOption(fileHandle(name).some)
              bytes <- ZStream
                        .fromInputStream(Files.newInputStream(ref.path))
            } yield bytes

          override def file(name: String): ZIO[Any, Nothing, Option[FileMeta]] =
            fileHandle(name)
        })
        .asService

    val empty: Layer[Nothing, Uploads] =
      ZLayer.succeed(new Service {
        override def stream(name: String): ZStream[Blocking, Throwable, Byte] = Stream.empty

        override def file(name: String): ZIO[Any, Nothing, Option[FileMeta]] = ZIO.none
      })
  }

  def stream(name: String): ZStream[Uploads with Blocking, Throwable, Byte] =
    ZStream.accessStream(_.get.stream(name))

  def fileMeta(name: String): ZIO[Uploads, Nothing, Option[FileMeta]] =
    ZIO.accessM(_.get.file(name))

}
