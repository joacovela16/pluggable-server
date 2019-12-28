package jvc.serverplug

import java.net.URLClassLoader
import java.util.Date

import akka.http.scaladsl.model.DateTime
import jvc.prototype.pluggable.sdk.Registry

package object engine {
  type PluginID = String
  type RegistryID = String

  sealed trait Plugin {
    def id: PluginID


  }

  case class PluginInstalled(id: PluginID, installedDate: DateTime, classLoader: URLClassLoader, registry: Map[RegistryID, Registry], active: Boolean) extends Plugin

  case class PluginUninstalled(id: PluginID) extends Plugin

  case class PluginProxy(id: PluginID, installedDate: Option[DateTime], installed: Boolean, active: Boolean, registry: Seq[RegistryID])

  case class PluginState(plugins: Seq[PluginProxy])

  def buildState(xs: Iterable[Plugin]): PluginState = PluginState(
    xs.map {
      case PluginInstalled(id, installedDate, _, registry, active) =>
        PluginProxy(
          id,
          Option(installedDate),
          true,
          active,
          registry.values.map(_.identifier).toSeq
        )

      case PluginUninstalled(id) =>

        PluginProxy(
          id,
          None,
          false,
          false,
          Nil
        )

    }.toSeq
  )
}
