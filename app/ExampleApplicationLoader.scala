import controllers.GraphQLController
import play.api.mvc.EssentialFilter
import play.api.routing.Router
import play.api.routing.sird._
import play.api.{ Application, ApplicationLoader, BuiltInComponentsFromContext }
import play.filters.cors.{ CORSConfig, CORSFilter }
import services.{ ExampleApi, ExampleData, ExampleService }
import zio.Runtime
import zio.ZEnv
import zio.internal.Platform

class ExampleApplicationLoader extends ApplicationLoader {
  override def load(context: ApplicationLoader.Context): Application = new ExampleComponents(context).application
}

class ExampleComponents(context: ApplicationLoader.Context) extends BuiltInComponentsFromContext(context) {

  implicit val runtime = Runtime.unsafeFromLayer(
    ExampleService.make(ExampleData.sampleCharacters) ++
      ZEnv.live,
    Platform.default
  )

  val interpreter = runtime.unsafeRun(ExampleApi.api.interpreter)
  val controller  = new GraphQLController(controllerComponents)

  override def router: Router = Router.from {
    case POST(p"/graphql") => controller.graphQL(interpreter)
  }

  override def httpFilters: Seq[EssentialFilter] = Seq(CORSFilter(CORSConfig(CORSConfig.Origins.All)))

}
