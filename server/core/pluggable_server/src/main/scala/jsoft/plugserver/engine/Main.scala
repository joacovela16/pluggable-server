package jsoft.plugserver.engine

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{HttpApp, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._
import jsoft.plugserver.engine.core.PluginCore
import jsoft.plugserver.engine.util.PluginImplicitsSupport

object Main extends HttpApp with FailFastCirceSupport with PluginImplicitsSupport with PluginCore {

  override protected def routes: Route = concat(
    (get & pathPrefix("app")) {
      pathEndOrSingleSlash {
        get(getFromResource("app/index.html"))
      } ~
        getFromResourceDirectory("app")
    },
    path("state") {
      get(complete(StatusCodes.OK, getPluginsState))
    },
    path("config" / "plugin" / Segment / Segment / "reload") { (pluginID, serviceID) =>
      get(reloadService(pluginID, serviceID))
    },
    path("config" / "plugin" / Segment / Segment) { (pluginId, action) =>
      get {
        action match {
          case "enable" => pluginAsEnable(pluginId)
          case "disable" => pluginAsDisable(pluginId)
          case "install" => taskAddEntry(pluginId, installing = true)
          case "uninstall" => taskRemoveEntry(pluginId)
          case _ => complete(StatusCodes.BadRequest, "Undefined operation")
        }
      }
    },
    path("config" / "service" / Segment / Segment / Segment) { (pluginID, serviceID, action) => taskServiceStatus(pluginID, serviceID, action) },
    path("service" / Segment / Segment) { (pluginID, registryID) =>
      redirectToService(pluginID, registryID).getOrElse(complete(StatusCodes.NotFound))
    }
  )

  def main(args: Array[String]): Unit = {
    taskLoadCreated()
    taskWatchJars()
    startServer("localhost", 8080)
    taskStopWatch()
  }
}
