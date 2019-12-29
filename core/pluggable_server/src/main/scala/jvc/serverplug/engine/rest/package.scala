package jvc.serverplug.engine

import akka.http.scaladsl.model.DateTime

package object rest {

  case class PluginProxy(id: PluginID, installedDate: Option[DateTime], installed: Boolean, active: Boolean, registry: Seq[RegistryID])

  case class PluginState(plugins: Seq[PluginProxy])

}
