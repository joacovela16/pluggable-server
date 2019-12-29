package jvc.prototype.sub

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import jvc.prototype.pluggable.sdk.RestRegistry

class JoaquinRecord extends RestRegistry with Directives {

  override def route: Route = get {
    import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
    val values: Array[Double] = Array(65, 51, 16, 11, 6519, 191, 0, 98, 19854, 1, 32)
    val descriptiveStatistics: DescriptiveStatistics = new DescriptiveStatistics
    for (v <- values) {
      descriptiveStatistics.addValue(v)
    }

    val mean: Double = descriptiveStatistics.getMean
    val median: Double = descriptiveStatistics.getPercentile(50)
    val standardDeviation: Double = descriptiveStatistics.getStandardDeviation
    complete(StatusCodes.OK, s"BIENNNNNN!!!! $standardDeviation")
  }

  override def identifier: String = "operation"
}
