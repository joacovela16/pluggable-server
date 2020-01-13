package jsoft.plugserver.engine.model

import akka.http.scaladsl.model.DateTime
import jsoft.plugserver.engine.model.Types.{PluginID, ServiceID}

case class ServiceInfo(id: ServiceID, active: Boolean, category: String, description: String)

case class PluginInfo(id: PluginID, installedDate: Option[DateTime], installed: Boolean, active: Boolean, services: Seq[ServiceInfo])
