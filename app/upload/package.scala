import zio.stream.{ Stream, ZStream }
import zio.{ Has, Layer, ZIO, ZLayer }

package object upload {
  type Uploads = Has[Uploads.Service]

  object Uploads {
    trait Service {
      def stream(name: String): Stream[Throwable, Byte]
    }

    def handler(fileHandle: String => ZStream[Any, Throwable, Byte]): ZIO[Any, Nothing, Uploads] =
      ZIO
        .succeed(new Service {
          override def stream(name: String): Stream[Throwable, Byte] =
            fileHandle(name)
        })
        .asService

    val empty: Layer[Nothing, Uploads] =
      ZLayer.succeed(new Service {
        override def stream(name: String): Stream[Throwable, Byte] = Stream.empty
      })
  }

  def stream(name: String): ZStream[Uploads, Throwable, Byte] =
    ZStream.accessStream(_.get.stream(name))

}
