package jvc.serverplug.engine.model

import akka.http.scaladsl.model.DateTime
import jvc.serverplug.engine.model.Types.{PluginID, RegistryID}

case class PluginProxy(id: PluginID, installedDate: Option[DateTime], installed: Boolean, active: Boolean, registry: Seq[RegistryID])
