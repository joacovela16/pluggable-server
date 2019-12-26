package jvc.serverplug

import java.net.URLClassLoader

import jvc.prototype.pluggable.sdk.Registry

package object engine {
  type PluginID = String
  type RegistryID = String


  case class Plugin(id: PluginID, classLoader: URLClassLoader, registry: Map[RegistryID, Registry], active: Boolean = false)

}
