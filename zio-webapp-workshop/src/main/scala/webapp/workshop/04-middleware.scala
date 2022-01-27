package webapp.workshop

import java.io.IOException

import zio._
import zio.json._
import zio.test._
import zio.test.TestAspect._
import zhttp.http._
import zhttp.http.middleware.HttpMiddleware
import io.netty.handler.codec.http.cors.CorsConfig
import zio.logging.LogAnnotation

object MiddlewareSpec extends ZIOSpecDefault {

  final case class User(name: String, email: String, id: String)
  object User {
    implicit val codec: JsonCodec[User] = DeriveJsonCodec.gen[User]
  }
  sealed trait UsersRequest
  object UsersRequest {
    final case class Get(id: Int)                        extends UsersRequest
    final case class Create(name: String, email: String) extends UsersRequest

    implicit val codec: JsonCodec[UsersRequest] = DeriveJsonCodec.gen[UsersRequest]
  }
  sealed trait UsersResponse
  object UsersResponse {
    final case class Got(user: User)     extends UsersResponse
    final case class Created(user: User) extends UsersResponse

    implicit val codec: JsonCodec[UsersResponse] = DeriveJsonCodec.gen[UsersResponse]
  }

  //
  // TOUR
  //
  val helloWorld =
    Http.collect[Request] { case Method.GET -> !! / "greet" =>
      Response.text("Hello World!")
    } @@ Middleware.debug

  //
  // TYPES
  //

  // type Middleware[R, E, AIn, BIn, AOut, BOut] = Http[R, E, AIn, BIn] => Http[R, E, AOut, BOut]

  //
  // CONSTRUCTORS
  //

  /**
   * EXERCISE
   *
   * Using `Middleware.cors()`, construct a middleware for Cross-Origin Resource
   * Sharing (CORS).
   */
  lazy val corsMiddleware: HttpMiddleware[Any, Nothing] = ???
  // Middleware.cors(Cors.CorsConfig(
  //   ???
  // ))

  /**
   * EXERCISE
   *
   * Using `Middleware.debug`, construct a middleware for debugging status,
   * method, URL, and response timing.
   */
  lazy val debugMiddleware: HttpMiddleware[Console with Clock, IOException] =
    Middleware.debug

  /**
   * EXERCISE
   *
   * Using `Middleware.addCookie`, construct a middleware that adds the
   * specified cookie to responses.
   */
  val testCookie = Cookie("sessionId", "12345")
  lazy val cookieMiddleware: HttpMiddleware[Any, Nothing] =
    Middleware.addCookie(testCookie)

  /**
   * EXERCISE
   *
   * Using `Middleware.timeout`, construct a middleware that times out requests
   * that take longer than 2 minutes.
   */
  lazy val timeoutMiddleware: HttpMiddleware[Clock, Nothing] =
    Middleware.timeout((2.minutes))

  /**
   * EXERCISE
   *
   * Using `Middleware.runBefore`, construct a middleware that prints out
   * "Starting to process request!" before the request is processed.
   */
  lazy val beforeMiddleware: HttpMiddleware[Console, Nothing] =
    Middleware.runBefore(Console.printLine("Starting to process request").ignore)

  /**
   * EXERCISE
   *
   * Using `Middleware.runAfter`, construct a middleware that prints out "Done
   * with request!" after each request.
   */
  lazy val afterMiddleware: HttpMiddleware[Console, Nothing] =
    Middleware.runAfter(Console.printLine("Ending process request").ignore)

  /**
   * EXERCISE
   *
   * Using `Middleware.basicAuth`, construct a middleware that performs fake
   * authorization for any user who has password "abc123".
   */
  lazy val authMiddleware: HttpMiddleware[Any, Nothing] =
    Middleware.basicAuth {
      case (_, "abc123") => true
      case _             => false
    }

  /**
   * EXERCISE
   *
   * Using `Middleware.codecZIO`, create a middleware that can decode JSON into
   * some type `In`, and encode some type `Out` into JSON, allowing the
   * definition of Http functions that do not work directly on Request/Response,
   * but rather some user-defined data t ypes.
   */
  def codecMiddleware[In: JsonDecoder, Out: JsonEncoder]: Middleware[Any, Nothing, In, Out, Request, Response] =
    Middleware.codecZIO[Request, Out](
      request =>
        for {
          body    <- request.getBodyAsString.orDie
          decoded <- ZIO.fromEither(implicitly[JsonDecoder[In]].decodeJson(body))
        } yield decoded,
      out => ZIO.succeed(Response.json(JsonEncoder[Out].encodeJson(out, None).toString()))
    ) <> Middleware.succeed(Response.fromHttpError(HttpError.BadRequest("Invalid JSON")))

  //
  // OPERATORS
  //

  /**
   * EXERCISE
   *
   * Using `Http.@@`, apply the `codecMiddleware` to transform the following
   * `Http` into an `HttpApp`.
   */
  val usersService: Http[Any, Nothing, UsersRequest, UsersResponse] = Http.collect {
    case UsersRequest.Create(name, email) => UsersResponse.Created(User(name, email, "abc123"))
    case UsersRequest.Get(id)             => UsersResponse.Got(User(id.toString, "", ""))
  }
  lazy val usersServiceHttpApp: HttpApp[Any, Nothing] =
    usersService @@ codecMiddleware[UsersRequest, UsersResponse]

  /**
   * EXERCISE
   *
   * Using `Middleware.>>>` compose `beforeMiddleware`, `afterMiddleware`, and
   * `authMiddleware` to construct a middleware that performs each function in
   * sequence.
   */
  lazy val beforeAfterAndAuth1: HttpMiddleware[Console, Nothing] =
    beforeMiddleware >>> afterMiddleware >>> authMiddleware

  /**
   * EXERCISE
   *
   * Using `Middleware.++` compose `beforeMiddleware`, `afterMiddleware`, and
   * `authMiddleware` to construct a middleware that performs each function in
   * sequence.
   */
  lazy val beforeAfterAndAuth2: HttpMiddleware[Console, Nothing] =
    beforeMiddleware ++ afterMiddleware ++ authMiddleware

  //
  // GRADUATION
  //

  /**
   * EXERCISE
   *
   * Create middleware that logs requests using `ZIO.log*` family of functions.
   * For bonus points, integrate with ZIO Logging's LogFormat.
   */
  lazy val requestLogger: HttpMiddleware[Any, Nothing] =
    Middleware.identity.contramapZIO[Request] { request =>
      val time          = java.lang.System.currentTimeMillis()
      val path          = request.path
      val method        = request.method
      val remote        = request.remoteAddress
      val userAgent     = request.getUserAgent
      val contentLength = request.getContentLength

      ZIO.logDebug(s"Request: $time - $path - $method - $remote - $userAgent - $contentLength").as(request)
    }

  /**
   * EXERCISE
   *
   * Create middleware that logs responses using `ZIO.log*` family of functions.
   * For bonus points, integrate with ZIO Logging's LogFormat.
   */
  lazy val responseLogger: HttpMiddleware[Any, Nothing] =
    Middleware.interceptZIO[Request, Response](request => ZIO.succeed(java.lang.System.currentTimeMillis())) {
      case (response, (request, startTime)) =>
        ZIO.succeed(response)
    }

  import zio.logging._
  def logged[A](
    logAnn: LogAnnotation[A],
    proj: Headers => Option[A],
    unproj: A => Headers
  ): HttpMiddleware[Any, Nothing] =
    Middleware.interceptZIO[Request, Response] { request =>
      proj(request.getHeaders) match {
        case None => ZIO.succeed(None)

        case some @ Some(a) => logContext.update(_.annotate(logAnn, a)).as(some)
      }
    } {
      case (response, None) => ZIO.succeed(response)

      case (response, Some(a)) => ZIO.succeed(response.addHeaders(unproj(a)))
    }

  val traceId: HttpMiddleware[Any, Nothing] =
    logged(
      LogAnnotation.TraceId,
      headers => headers.getHeaderValue("X-Trace-Id").map(java.util.UUID.fromString(_)),
      (uuid: java.util.UUID) => Headers(("X-Trace-Id", uuid.toString))
    )

  def spec = suite("MiddlewareSpec") {
    suite("constructors") {
      test("cors") {
        assertTrue(corsMiddleware != null)
      } @@ ignore +
        test("debug") {
          assertTrue(debugMiddleware != null)
        } @@ ignore +
        test("cookie") {
          assertTrue(cookieMiddleware != null)
        } @@ ignore +
        test("timeout") {
          assertTrue(timeoutMiddleware != null)
        } @@ ignore +
        test("runBefore") {
          assertTrue(beforeMiddleware != null)
        } @@ ignore +
        test("runAfter") {
          assertTrue(afterMiddleware != null)
        } @@ ignore +
        test("basicAuth") {
          assertTrue(authMiddleware != null)
        } @@ ignore +
        test("codec") {
          val http: Http[Any, Nothing, UsersRequest, UsersResponse] = Http.collect {
            case UsersRequest.Create(name, email) => UsersResponse.Created(User(name, email, "abc123"))
            case UsersRequest.Get(id)             => UsersResponse.Got(User(id.toString, "", ""))
          }
          val http2 = http @@ codecMiddleware[UsersRequest, UsersResponse]

          assertTrue(http2 != null)
        } @@ ignore
    } +
      suite("operators") {
        test("Http.@@") {
          assertTrue(usersService != null)
        } @@ ignore +
          test("Middleware.andThen") {
            assertTrue(beforeAfterAndAuth1 != null)
          } @@ ignore +
          test("Middleware.combine") {
            assertTrue(beforeAfterAndAuth2 != null)
          } @@ ignore
      } +
      suite("graduation") {
        test("requestLogger") {
          assertTrue(requestLogger != null)
        } @@ ignore +
          test("responseLogger") {
            assertTrue(responseLogger != null)
          } @@ ignore
      }
  }
}
