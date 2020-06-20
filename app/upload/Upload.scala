package upload

import caliban.Value.{ NullValue, StringValue }
import caliban.schema.Annotations.GQLName
import caliban.schema.{ ArgBuilder, Schema }
import caliban.{ GraphQLRequest, InputValue }
import zio.blocking.Blocking
import zio.stream.{ ZSink, ZStream }
import zio.{ Chunk, RIO, UIO, URIO }

@GQLName("Upload")
sealed case class Upload(name: String) {

  val allBytes: RIO[Uploads with Blocking, Chunk[Byte]] =
    upload.stream(name).run(ZSink.foldLeftChunks(Chunk[Byte]())(_ ++ (_: Chunk[Byte])))

  val meta: URIO[Uploads, Option[FileMeta]] =
    upload.fileMeta(name)

}

object Upload {}

/**
 * Wraps a request which included an upload request,
 * this stores the additional information necessary to be able to resolve
 * files at query time
 */
case class GraphQLUploadRequest(
  request: GraphQLRequest,
  fileMap: List[(String, List[Either[String, Int]])], // This needs to be used to remap the input values
  fileHandle: UIO[Uploads]
) {

  /**
   * Override the variables values with their respective targets
   * @return
   */
  def remap: GraphQLRequest =
    request.copy(
      variables = request.variables.map { vars =>
        vars ++ fileMap.flatMap {
          case (name, Left("variables") :: Left(key) :: path) => vars.get(key).map(loop(_, path, name)).map(key -> _)
          case _                                              => None
        }
      }
    )

  /**
   * We need to continue stepping through the path until we find the point where we are supposed
   * to inject the string.
   */
  private def loop(value: InputValue, path: List[Either[String, Int]], name: String): InputValue =
    path.headOption match {
      case Some(Left(key)) =>
        value match {
          case InputValue.ObjectValue(fields) =>
            fields.get(key).fold[InputValue](NullValue)(loop(_, path.drop(1), name))
          case _ => NullValue
        }
      case Some(Right(idx)) =>
        value match {
          case InputValue.ListValue(values) =>
            values.lift(idx).fold[InputValue](NullValue)(loop(_, path.drop(1), name))
          case _ => NullValue
        }
      case None =>
        // If we are out of values then we are at the end of the path, so we need to replace this current node
        // with a string node containing the file name
        StringValue(name)
    }

}

object GraphQLUploadRequest {}
