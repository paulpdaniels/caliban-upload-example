package controllers

import java.util.Locale

import caliban.Value.NullValue
import caliban.{ GraphQLInterpreter, GraphQLRequest, GraphQLResponse }
import play.api.http.Writeable
import play.api.libs.json.{ JsValue, Json, Writes }
import play.api.mvc._
import services.ExampleEnv
import upload.{ FileMeta, GraphQLUploadRequest, Uploads }
import zio.blocking.Blocking
import zio.random.Random
import zio.{ random, Ref, Runtime, ZIO }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class GraphQLController(cc: ControllerComponents) extends AbstractController(cc) {

  def graphQL[E](
    interpreter: GraphQLInterpreter[ExampleEnv with Uploads, E],
    skipValidation: Boolean = false,
    enableIntrospection: Boolean = true
  )(implicit runtime: Runtime[ExampleEnv]): Action[Either[GraphQLUploadRequest, GraphQLRequest]] =
    Action.async(makeParser(runtime)) { req =>
      runtime.unsafeRunToFuture(
        req.body match {
          case Left(value) =>
            executeRequest(
              interpreter,
              value.remap,
              skipValidation,
              enableIntrospection
            ).provideSomeLayer[ExampleEnv](value.fileHandle.toLayerMany)

          case Right(value) =>
            executeRequest(
              interpreter,
              value,
              skipValidation,
              enableIntrospection
            ).provideSomeLayer[ExampleEnv](Uploads.empty)
        }
      )

    }

  implicit def writableGraphQLResponse[E](implicit wr: Writes[GraphQLResponse[E]]): Writeable[GraphQLResponse[E]] =
    Writeable.writeableOf_JsValue.map(wr.writes)

  private def parseJson(s: String): Try[JsValue] =
    Try(Json.parse(s))

  private def executeRequest[E](
    interpreter: GraphQLInterpreter[ExampleEnv with Uploads, E],
    request: GraphQLRequest,
    skipValidation: Boolean,
    enableIntrospection: Boolean
  ) =
    interpreter
      .executeRequest(request, skipValidation = skipValidation, enableIntrospection = enableIntrospection)
      .catchAllCause(cause => ZIO.succeed(GraphQLResponse[Throwable](NullValue, cause.defects)))
      .map(Ok(_))

  private def parsePath(path: String): List[Either[String, Int]] =
    path.split('.').map(c => Try(c.toInt).toEither.left.map(_ => c)).toList

  private def uploadFormParser(
    runtime: Runtime[Random]
  ): BodyParser[GraphQLUploadRequest] =
    parse.multipartFormData.validateM { form =>
      // First bit is always a standard graphql payload, it comes from the `operations` field
      val tryOperations =
        parseJson(form.dataParts("operations").head).map(_.as[GraphQLRequest])
      // Second bit is the mapping field
      val tryMap = parseJson(form.dataParts("map").head)
        .map(_.as[Map[String, Seq[String]]])

      runtime.unsafeRunToFuture(
        (for {
          operations <- ZIO
                         .fromTry(tryOperations)
                         .orElseFail(Results.BadRequest("Missing multipart field 'operations'"))
          map <- ZIO
                  .fromTry(tryMap)
                  .orElseFail(Results.BadRequest("Missing multipart field 'map'"))
          filePaths = map.view
            .mapValues(_.map(parsePath).toList)
            .toList
            .flatMap(kv => kv._2.map(kv._1 -> _))
          fileRef <- Ref.make(form.files.map(f => f.key -> f).toMap)
          rand    <- ZIO.environment[Random]
        } yield
          GraphQLUploadRequest(
            operations,
            filePaths,
            Uploads.handler(
              handle =>
                fileRef.get
                  .map(_.get(handle))
                  .some
                  .flatMap(
                    fp =>
                      random
                        .nextString(16)
                        .asSomeError
                        .map(
                          FileMeta(
                            _,
                            fp.ref.path,
                            fp.dispositionType,
                            fp.contentType,
                            fp.filename,
                            fp.fileSize
                          )
                      )
                  )
                  .optional
                  .provide(rand)
            )
          )).either
      )
    }(runtime.platform.executor.asEC)

  private def makeParser(runtime: Runtime[Blocking with Random]) =
    parse.using { req =>
      implicit val ec: ExecutionContext = runtime.platform.executor.asEC
      req.contentType.map(_.toLowerCase(Locale.ENGLISH)) match {
        case Some("text/json") | Some("application/json") =>
          parse.json[GraphQLRequest].map(Right(_))
        case Some("multipart/form-data") =>
          uploadFormParser(runtime).map(Left(_))
        case _ =>
          parse.error(Future.successful(Results.BadRequest("Invalid content type")))
      }
    }

}
