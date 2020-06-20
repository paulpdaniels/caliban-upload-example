import services.ExampleService.ExampleService
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.random.Random

package object services {
  type ExampleEnv = Clock with Console with ExampleService with Blocking with Random
}
