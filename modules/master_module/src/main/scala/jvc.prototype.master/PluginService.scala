package jvc.prototype.master

import java.io.File
import java.net.URLClassLoader
import java.nio.file._
import java.util.ServiceLoader

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{HttpApp, Route}
import com.typesafe.scalalogging.LazyLogging
import jvc.prototype.common.Registry

import scala.collection.mutable
import scala.util.Try

object PluginService extends HttpApp with LazyLogging {

  private type PluginID = String
  private type RegistryID = String

  case class Plugin(id: String, classLoader: URLClassLoader, registry: Map[RegistryID, Registry], active: Boolean = false)

  private val pluginStore: mutable.Map[PluginID, Plugin] = mutable.Map.empty

  private def registryStr(xs: Iterable[Registry]): String = xs.map(x => s"\tService: ${x.identifier}").mkString("\n")

  override protected def routes: Route = {
    path("state") {
      get {
        val state: String =
          s"""
             |Plugins:
             |
             |${pluginStore.map { case (k, v) => s"- $k active: ${v.active} \n${registryStr(v.registry.values)}" }.mkString("\n")}
             |""".stripMargin

        complete(StatusCodes.OK, state)
      }
    } ~
      path("plugin" / Segment / Segment) { (pluginId, action) =>
        get {

          action match {
            case "active" =>

              pluginStore.get(pluginId) match {
                case Some(plugin) =>

                  pluginStore.put(pluginId, Plugin(pluginId, plugin.classLoader, plugin.registry, active = true))
                  complete(StatusCodes.OK, s"Plugin $pluginId activated")

                case None => complete(StatusCodes.NotFound, s"Plugin $pluginId not founded")
              }

            case "disable" =>

              pluginStore.get(pluginId) match {
                case Some(plugin) =>

                  pluginStore.put(pluginId, Plugin(pluginId, plugin.classLoader, plugin.registry))
                  complete(StatusCodes.OK, s"Plugin $pluginId disabled")

                case None =>
                  complete(StatusCodes.NotFound, s"Plugin $pluginId not founded")
              }

            case "delete" =>

              pluginStore.get(pluginId) match {
                case Some(plugin) =>

                  plugin.classLoader.close()
                  pluginStore -= pluginId
                  complete(StatusCodes.OK, s"Plugin $pluginId deleted")
                case None =>
                  complete(StatusCodes.NotFound, s"Plugin $pluginId not founded")
              }

            case _ => complete(StatusCodes.BadRequest, "Undefined operation")
          }
        }
      } ~
      path("service" / Segment / Segment) { (pluginID, serviceID) =>
        pluginStore.get(pluginID).filter(_.active) match {
          case Some(plugin) => plugin.registry.get(serviceID) match {
            case Some(value) => value.route
            case None => complete(StatusCodes.NotImplemented, s"Servicio {$serviceID} no registrado")
          }
          case None => complete(StatusCodes.NotImplemented, s"Plugin {$pluginID} no registrado o  desactivado")
        }
      }
  }

  def taskAddEntry(filename: String, forceActive: Boolean = false): Unit = {

    Option(filename)
      .filter(_.endsWith(".jar"))
      .map(x => new File(s"plugins/$x"))
      .map(_.toURI.toURL)
      .flatMap(url => Try(URLClassLoader.newInstance(Array(url))).toOption)
      .flatMap { ucl => Try(ServiceLoader.load(classOf[Registry], ucl)).toOption.map(x => (ucl, x)) }
      .foreach { case (ucl, srv) =>

        val lastIndexOf: Int = filename.lastIndexOf(".")
        val pluginID: PluginID = filename.substring(0, lastIndexOf)

        val tmpMap: mutable.Map[RegistryID, Registry] = mutable.Map.empty
        srv.forEach(x => tmpMap.put(x.identifier, x))

        logger.info(s"Plugin $pluginID installed. Included ${tmpMap.size} services")

        pluginStore.put(pluginID, Plugin(pluginID, ucl, tmpMap.toMap, forceActive))
      }
  }

  def main(args: Array[String]): Unit = {

    new File("plugins")
      .list { case (_, name: String) => name.endsWith(".jar") }
      .foreach(x => taskAddEntry(x, forceActive = true))

    val thread: Thread = new Thread {
      override def run() {

        val watchService: WatchService = FileSystems.getDefault.newWatchService()

        Paths.get("plugins").register(watchService,
          StandardWatchEventKinds.ENTRY_MODIFY
        )

        while (true) {
          val key: WatchKey = watchService.take()
          key.pollEvents().forEach { x =>

            val context: String = x.context().toString

            x.kind().toString match {
              case "ENTRY_MODIFY" => taskAddEntry(context)
            }
          }
        }
      }
    }

    thread.start()

    startServer("localhost", 8080)
  }
}
