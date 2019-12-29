package jvc.serverplug.engine

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{HttpApp, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._
import jvc.serverplug.engine.core.PluginCore
import jvc.serverplug.engine.util.PluginImplicitsSupport

object Main extends HttpApp with FailFastCirceSupport with PluginImplicitsSupport with PluginCore {

  override protected def routes: Route = {
    path("app") {
      pathEnd {
        get(getFromResource("web/index.html"))
      }
    } ~
      path("app" / Segment) { r => get(getFromResource(s"web/$r")) } ~
      path("state") {
        get(complete(StatusCodes.OK, getPluginsState))
      } ~
      path("plugin" / Segment / Segment / "reload") { (pluginID, registryID) =>
        get(reloadService(pluginID, registryID))
      } ~
      path("plugin" / Segment / Segment) { (pluginId, action) =>
        get {
          action match {
            case "enable" => pluginAsEnable(pluginId)
            case "disable" => pluginAsDisable(pluginId)
            case "install" => taskAddEntry(pluginId, true)
            case "uninstall" => taskRemoveEntry(pluginId)
            case _ => complete(StatusCodes.BadRequest, "Undefined operation")
          }
        }
      } ~
      path("service" / Segment / Segment) { (pluginID, registryID) =>
        redirectToService(pluginID, registryID).getOrElse(complete(StatusCodes.NotFound))
      }
  }

  def main(args: Array[String]): Unit = {
    taskLoadCreated()
    taskWatchJars()
    startServer("localhost", 8080)
    taskStopWatch()
  }
}
