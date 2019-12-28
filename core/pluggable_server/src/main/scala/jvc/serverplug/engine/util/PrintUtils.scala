package jvc.serverplug.engine.util

import jvc.serverplug.engine.Plugin

object PrintUtils {

  def printReport(plugins: Seq[Plugin]): String = {

    <html>
      <head>
        <title>Testing</title>
      </head>
      <body>
        <details open="">
          {plugins.map { plug =>
          <summary>
            <b>
              {plug.id}
              (
              {if (plug.active) "enable" else "disable"}
              )
            </b>
          </summary>
            <div>
              {if (plug.active) {
              val url: String = s"/plugin/${plug.id}/disable?${System.nanoTime()}"
              <a href={url}>Set as disable</a>
            } else {
              val url: String = s"/plugin/${plug.id}/enable?${System.nanoTime()}"
              <a href={url}>Set as enable</a>
            }}
            </div>
        }}
        </details>
      </body>
    </html>
    }.toString()
}
