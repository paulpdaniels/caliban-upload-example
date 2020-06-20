package services

import caliban.GraphQL.graphQL
import caliban.ResponseValue.ObjectValue
import caliban.schema.Annotations.{ GQLDeprecated, GQLDescription }
import caliban.schema.{ ArgBuilder, GenericSchema }
import caliban.wrappers.ApolloTracing.apolloTracing
import caliban.wrappers.Wrappers.{ maxDepth, maxFields }
import caliban.{ GraphQL, RootResolver }
import services.ExampleData._
import services.ExampleService.ExampleService
import upload.{ Upload, Uploads }
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.stream.ZStream
import zio.{ console, UIO, URIO }

import scala.language.postfixOps

object ExampleApi extends GenericSchema[ExampleService with Uploads with Clock with Console with Blocking] {

  case class ID(value: String) extends AnyVal

  case class Queries(
    @GQLDescription("Return all characters from a given origin")
    characters: CharactersArgs => URIO[ExampleService, List[Character]],
    @GQLDeprecated("Use `characters`")
    character: CharacterArgs => URIO[ExampleService, Option[Character]]
  )

  case class File(id: ID, path: String, filename: String, mimetype: String)

  implicit val argBuilder: ArgBuilder[Upload]                    = ArgBuilder.string.map(Upload(_))
  implicit val uploadSchema: Typeclass[Upload]                   = scalarSchema("Upload", None, _ => ObjectValue(Nil))
  implicit val uploadFileArgsSchema: Typeclass[UploadFileArgs]   = gen[UploadFileArgs]
  implicit val uploadFilesArgsSchema: Typeclass[UploadFilesArgs] = gen[UploadFilesArgs]
  implicit val roleSchema                                        = gen[Role]
  implicit val characterSchema                                   = gen[Character]
  implicit val characterArgsSchema                               = gen[CharacterArgs]
  implicit val charactersArgsSchema                              = gen[CharactersArgs]

  case class Mutations(
    singleUpload: UploadFileArgs => URIO[Console with Uploads with Blocking, File],
    multipleUpload: UploadFilesArgs => UIO[Boolean]
  )
  case class Subscriptions(characterDeleted: ZStream[ExampleService, Nothing, String])

  implicit val mutationsSchema: Typeclass[Mutations] = gen[Mutations]

  val api: GraphQL[Console with Clock with ExampleService with Uploads with Blocking] =
    graphQL(
      RootResolver(
        Queries(
          args => ExampleService.getCharacters(args.origin),
          args => ExampleService.findCharacter(args.name)
        ),
        Mutations(
          args =>
            (for {
              bytes <- args.file.allBytes
              _     <- console.putStrLn(new String(bytes.toArray))
              f     <- args.file.meta.someOrFailException
            } yield File(ID(f.id), f.path.toString, f.fileName, f.dispositionType)).orDie,
          args => UIO.succeed(true)
        )
      )
    ) @@
      maxFields(200) @@ // query analyzer that limit query fields
      maxDepth(30) @@   // query analyzer that limit query depth
//      timeout(3 seconds) @@           // wrapper that fails slow queries
//      printSlowQueries(500 millis) @@ // wrapper that logs slow queries
      apolloTracing // wrapper for https://github.com/apollographql/apollo-tracing

}
