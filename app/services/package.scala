import services.ExampleService.ExampleService
import upload.Uploads
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console

package object services {
  type ExampleEnv = Clock with Console with ExampleService with Blocking
}
