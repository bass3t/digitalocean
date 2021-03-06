package me.jeffshaw.digitalocean

import com.ning.http.client.Response
import dispatch.Defaults._
import dispatch._
import org.json4s._, native._

import scala.concurrent.duration._

/**
 *
 * @param token Your API token.
 * @param maxWaitPerRequest The maximum amount of time the client should wait for a response before assuming the service is down.
 * @param actionCheckInterval The amount of time to wait between checks for an action to complete.
 */
case class DigitalOceanClient(
  private val token: String,
  maxWaitPerRequest: Duration,
  actionCheckInterval: Duration
) {
  private val requestPrefix =
    DigitalOceanClient.host.addHeader("Authorization", "Bearer " + token)

  /**
   * This needs to be used carefully, because it can potentially give
   * the api key to a 3rd party.
   * @param req
   * @tparam T
   * @return
   */
  private[digitalocean] def customRequest[T: Manifest](req: Req): Future[T] = {
    parseResponse[T](Http(req.addHeader("Authorization", "Bearer " + token)))
  }

  def setPath(pathElements: Seq[String]): Req = {
    pathElements.foldLeft(requestPrefix)((accum, pathElement) => accum / pathElement)
  }

  def delete(path: String*): Future[Unit] = {
    val request = Http(setPath(path) DELETE)

    for {
      response <- request
    } yield {
      if(response.getStatusCode >= 300) {
        throw new DigitalOceanClientException(response)
      }
    }
  }

  def parseResponse[T: Manifest](request: Future[Response]): Future[T] = {
    for {
      response <- request
    } yield {
      val responseBody = response.getResponseBody

      val statusCode = response.getStatusCode

      if (statusCode < 300 &&
        response.getContentType == DigitalOceanClient.contentType
      ) {
        parseJson(responseBody).camelizeKeys.extract[T]
      } else {
        throw DigitalOceanClientException(response)
      }
    }
  }

  def get[T: Manifest](path: String*): Future[T] = {
    val request = Http(setPath(path) GET)
    parseResponse[T](request)
  }

  def exists(path: String*): Future[Boolean] = {
    val request = Http(setPath(path) HEAD)
    for {
      response <- request
    } yield {
      response.getStatusCode != 404
    }
  }

  def post[T: Manifest](message: JValue, path: String*): Future[T] = {
    val messageBody = JsonMethods.compact(JsonMethods.render(message.snakizeKeys))
    val request = Http(setPath(path) setBody(messageBody) POST)
    parseResponse[T](request)
  }

  def put[T: Manifest](message: JValue, path: String*): Future[T] = {
    val messageBody = JsonMethods.compact(JsonMethods.render(message.snakizeKeys))
    val request = Http(setPath(path) setBody(messageBody) PUT)
    parseResponse[T](request)
  }
}

object DigitalOceanClient {
  val host = dispatch.host("api.digitalocean.com").secure.setContentType("application/json", "utf-8") / "v2"

  val contentType = "application/json; charset=utf-8"
}
