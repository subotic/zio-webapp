/**
 * METRICS
 *
 * The uptime, latency, and throughput of web applications directly impacts a
 * company's bottom-line, leading to very high pressure on the engineering to
 * ensure applications are performing as specified 24x7.
 *
 * In order to facilitate these goals, best-practice web applications must
 * collect and report on dozens of key metrics that offer insight into any
 * potential problems and provide assistance to diagnosing production issues.
 *
 * Although ZIO applications can use any JVM metrics library for collecting and
 * reporting such metrics, ZIO 2.0 brings built-in metrics to the table,
 * including both standardized metrics for the ZIO runtime, as well as a
 * general-purpose, pluggable means of adding user-defined metrics.
 *
 * In this section, you will learn how to create your own custom metrics, create
 * ZIO HTTP middleware for exporting new metrics, and how to plug ZIO 2.0
 * metrics into an external monitoring application.
 */
package webapp.workshop

import zio._
import zio.test._
import zio.test.TestAspect.ignore

import zhttp.http._
import zhttp.http.middleware._

object MetricsSpec extends ZIOSpecDefault {

  /**
   * EXERCISE
   *
   * Using `ZIOMetric.count`, create a counter called `web-requests`.
   */
  lazy val webRequestsCounter: ZIOMetric.Counter[Any] =
    ZIOMetric.count("web-requests")

  // databaseCall("foo") @@ databaseCounter // ZIOMetric[Any]
  // databaseCall("foo") <* databaseCounter.increment

  /**
   * EXERCISE
   *
   * Using `ZIOMetric.observeDurations`, create a histogram called
   * `web-request-durations` that will keep track of the durations of web
   * requests.
   */
  lazy val requestDurations: ZIOMetric.Histogram[Any] =
    ZIOMetric.observeDurations("web-request-durations", ZIOMetric.Histogram.Boundaries.linear(0, 100, 100))(
      _.toMillis.toDouble
    )

  /**
   * EXERCISE
   *
   * Using `ZIOMetric.setGaugeWith`, create a gauge called
   * `database-connections`, which will count database connections on an effect
   * producing an `Int` (which represents the number of active connections).
   */
  lazy val databaseConnectionGauge: ZIOMetric.Gauge[Int] =
    ZIOMetric.setGaugeWith("database-connections")(_.toInt)

  /**
   * EXERCISE
   *
   * Using `ZIOMetric.observeSummary`, create a summary metric to be used for
   * tracking request durations on a sliding window of 60 minutes.
   */
  lazy val requestDurationsSummary: ZIOMetric.Summary[Double] =
    ZIOMetric.observeSummary("request-duration", 60.minutes, 1000, 0.0, Chunk(0.9, 0.95, 0.99))

  /**
   * EXERCISE
   *
   * Using `ZIOMetric.occurrencesWith`, create a `SetCount` metric that keeps
   * track of the number of occurrences of each HTTP response status code.
   */
  lazy val httpResponseStatusCodes: ZIOMetric.SetCount[Int] =
    ZIOMetric.occurrencesWith("http-response-status-codes", "count")(_.toString)

  httpResponseStatusCodes.observe("200") // add value to histogram

  //
  // METRICS USAGE
  //

  /**
   * EXERCISE
   *
   * Create an `HttpMiddleware` that counts the number of requests, using the
   * `webRequestsCounter` metric.
   */
  lazy val webRequestsMiddleware: HttpMiddleware[Any, Nothing] =
    Middleware.runAfter(webRequestsCounter.increment)

  /**
   * EXERCISE
   *
   * Create an `HttpMiddleware` that observes the durations of requests, using
   * the `requestDurations` metric.
   */
  lazy val requestsDurationsMiddleware: HttpMiddleware[Any, Nothing] =
    Middleware.interceptZIO[Request, Response](
      request => ZIO.succeed(java.lang.System.currentTimeMillis()),
      (request, startTime) => {
        val endTime = java.lang.System.currentTimeMillis()
        val delta   = endTime - startTime

        requestDurations.observe(delta.toDouble).as(request)
      }
    )

  /**
   * EXERCISE
   *
   * Augment this database connection tracker with the ability to adjust the
   * `databaseConnectionGauge` metric.
   */
  class DatabaseConnectionTracker(ref: Ref[Int]) {
    def increment: UIO[Int] = ref.updateAndGet(_ + 1) @@ databaseConnectionGauge

    def decrement: UIO[Int] = ref.updateAndGet(_ - 1) @@ databaseConnectionGauge
  }

  /**
   * EXERCISE
   *
   * Create an `HttpMiddleware` that observes the HTTP response status codes,
   * using the `httpResponseStatusCodes` metric.
   */
  lazy val httpResponseStatusCodesMiddleware: HttpMiddleware[Any, Nothing] =
    Middleware.identity.mapZIO[Response] { (response: Response) =>
      httpResponseStatusCodes.observe(response.status.asJava.code().toString).as(response)
    }

  //
  // GRADUATION
  //

  import zio.metrics.MetricClient

  /**
   * EXERCISE
   *
   * Using the `unsafeSnapshot` method of `MetricClient`, create an
   * `HttpMiddleware` that intercepts any request to `/healthcheck`, and which
   * dumps out the metrics as JSON.
   */
  lazy val metricsMiddleware: HttpMiddleware[Any, Nothing] =
    Middleware.interceptZIO[Request, Response] {
      case Method.GET -> !! / "healthcheck" =>
        ZIO.succeed(Some(MetricClient.unsafeStates))
      case request => ZIO.succeed(None)
    } {
      case (response, None)  => ZIO.succeed(response)
      case (_, Some(metric)) => ZIO.succeed(Response.text(metric.toString()))
    }

  def spec = suite("MetricsSpec") {
    suite("metrics construction") {
      test("web requests") {
        assertTrue(webRequestsCounter != null)
      } @@ ignore +
        test("request durations") {
          assertTrue(requestDurations != null)
        } @@ ignore +
        test("database connections") {
          assertTrue(databaseConnectionGauge != null)
        } @@ ignore +
        test("requests durations summary") {
          assertTrue(requestDurationsSummary != null)
        } @@ ignore +
        test("http response status codes") {
          assertTrue(httpResponseStatusCodes != null)
        } @@ ignore
    } +
      suite("metrics usage") {
        test("web requests") {
          assertTrue(webRequestsMiddleware != null)
        } @@ ignore +
          test("request durations") {
            assertTrue(requestsDurationsMiddleware != null)
          } @@ ignore +
          test("http response status codes") {
            assertTrue(httpResponseStatusCodesMiddleware != null)
          } @@ ignore
      } +
      suite("graduation") {
        test("end-to-end") {
          assertTrue(metricsMiddleware != null)
        } @@ ignore
      }
  }
}
