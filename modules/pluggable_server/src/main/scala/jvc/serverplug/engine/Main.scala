package jvc.serverplug.engine

import java.io.File
import java.net.URLClassLoader
import java.nio.file._
import java.util.ServiceLoader

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.{HttpApp, Route, StandardRoute}
import com.typesafe.scalalogging.LazyLogging
import jvc.prototype.pluggable.sdk.{Registry, RestRegistry}
import jvc.serverplug.engine.util.PrintUtils

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.Try
import scala.concurrent.ExecutionContext.Implicits.global

object Main extends HttpApp with LazyLogging {

  private val pluginStore: mutable.Map[PluginID, Plugin] = mutable.Map.empty

  def taskSendReport: StandardRoute = {
    complete(StatusCodes.OK, HttpEntity(ContentTypes.`text/html(UTF-8)`, PrintUtils.printReport(pluginStore.values.toSeq)))
  }

  override protected def routes: Route = {
    path("state") {
      get {
        taskSendReport
      }
    } ~
      path("plugin" / Segment / Segment) { (pluginId, action) =>
        get {

          action match {
            case "enable" =>

              pluginStore.get(pluginId) match {
                case Some(plugin) =>

                  pluginStore.put(pluginId, Plugin(pluginId, plugin.classLoader, plugin.registry, active = true))
                  Future(plugin.registry.values.foreach(_.onActive()))
                  taskSendReport

                case None => complete(StatusCodes.NotFound, s"Plugin $pluginId not founded")
              }

            case "disable" =>

              pluginStore.get(pluginId) match {
                case Some(plugin) =>

                  pluginStore.put(pluginId, Plugin(pluginId, plugin.classLoader, plugin.registry))
                  Future(plugin.registry.values.foreach(_.onSuspend()))
                  taskSendReport

                case None =>
                  complete(StatusCodes.NotFound, s"Plugin $pluginId not founded")
              }

            case "delete" =>

              taskRemoveEntry(pluginId)

              taskSendReport

            case _ => complete(StatusCodes.BadRequest, "Undefined operation")
          }
        }
      } ~
      path("service" / Segment / Segment) { (pluginID, serviceID) =>
        pluginStore.get(pluginID).filter(_.active) match {
          case Some(plugin) => plugin.registry.get(serviceID) match {
            case Some(value) =>
              value match {
                case registry: RestRegistry => registry.route
                case _ => complete(StatusCodes.BadRequest)
              }
            case None => complete(StatusCodes.NotImplemented, s"Undefined service {$serviceID}")
          }
          case None => complete(StatusCodes.NotImplemented, s"Plugin {$pluginID} unknown or disabled")
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

        if (forceActive) {
          tmpMap.values.foreach { r => Future(r.onStart()) }
        }
      }
  }

  def taskRemoveEntry(pluginId: PluginID): Unit = {
    pluginStore.get(pluginId).foreach { plugin =>
      plugin.classLoader.close()
      pluginStore -= pluginId
      Future(plugin.registry.values.foreach(_.onDestroy()))
    }
  }

  def taskLoadCreated(): Unit = {
    new File("plugins")
      .list { case (_, name: String) => name.endsWith(".jar") }
      .foreach(x => taskAddEntry(x, forceActive = true))
  }

  def taskWatchJars(): Unit = {
    new Thread {
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
    }.start()
  }

  def taskRunServer(): Unit = startServer("localhost", 8080)

  def main(args: Array[String]): Unit = {

    taskLoadCreated()
    taskWatchJars()
    taskRunServer()
  }
}
