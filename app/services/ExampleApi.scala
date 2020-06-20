package services

import caliban.GraphQL.graphQL
import caliban.ResponseValue.ObjectValue
import caliban.schema.Annotations.{ GQLDeprecated, GQLDescription }
import caliban.schema.{ ArgBuilder, GenericSchema, Schema }
import caliban.wrappers.Wrappers.{ maxDepth, maxFields, printSlowQueries, timeout }
import caliban.wrappers.ApolloTracing.apolloTracing
import caliban.{ GraphQL, RootResolver }
import services.ExampleData._
import services.ExampleService.ExampleService
import upload.{ Upload, Uploads }
import zio.{ console, UIO, URIO }
import zio.clock.Clock
import zio.console.Console
import zio.stream.ZStream

import scala.language.postfixOps

object ExampleApi extends GenericSchema[ExampleService with Uploads with Clock with Console] {

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
    singleUpload: UploadFileArgs => URIO[Console with Uploads, File],
    multipleUpload: UploadFilesArgs => UIO[Boolean]
  )
  case class Subscriptions(characterDeleted: ZStream[ExampleService, Nothing, String])

  implicit val mutationsSchema: Typeclass[Mutations] = gen[Mutations]

  val api: GraphQL[Console with Clock with ExampleService with Uploads] =
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
            } yield File(ID("a"), "blob.txt", "blob.txt", "txt")).orDie,
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
