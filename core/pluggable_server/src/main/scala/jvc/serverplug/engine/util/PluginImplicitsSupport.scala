package jvc.serverplug.engine.util

import jvc.serverplug.engine.plugin.PluginInstalled

trait PluginImplicitsSupport {

  implicit class PluginInstalledExt(x: PluginInstalled) {
    def asEnable: PluginInstalled = PluginInstalled(x.id, x.installedDate, x.urls, true)

    def asDisable: PluginInstalled = PluginInstalled(x.id, x.installedDate, x.urls, false)
  }

}
