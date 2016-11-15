package io.amient.crawler.service

import java.io.{ByteArrayInputStream, StringWriter}
import java.net.{URI, URL}
import java.nio.file.{Files, Paths}

import dispatch.{url, _}
import io.amient.crawler.domain.Page
import org.htmlcleaner.{HtmlCleaner, SimpleXmlSerializer, TagNode}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

/**
  * Created by mharis on 29/07/2016.
  */
abstract class Scanner(val target: URL) {
  val targetPath = target.getPath.reverse.dropWhile(_ == '/').reverse

  val cleaner = new HtmlCleaner()
  val anchorExtractor: URLExtractor = new SimpleAnchorExtractor
  val assetExtractor: URLExtractor = new StaticAssetExtractor


  def close(): Unit

  protected def fetch(pageUrl: URL): Future[Either[Throwable, Array[Byte]]]

  def computeHash(doc: TagNode): Int = {
    val writer = new StringWriter()
    doc.serialize(new SimpleXmlSerializer(cleaner.getProperties), writer)
    writer.toString.hashCode
  }

  /**
    * scan a single page
    *
    * @param docUrl url to scan and create Page object from
    * @return a future of either throwable or Page
    */
  def scan(docUrl: URL): Future[Either[Throwable, Page]] = try {
    val maybeHttpData = fetch(docUrl)
    for (httpData <- maybeHttpData.right) yield {
      val doc = cleaner.clean(new ByteArrayInputStream(httpData))
      val hash = computeHash(doc)
      val links = anchorExtractor.extractFrom(doc).map(l => linkToAbsoluteURL(docUrl.toURI, l)).flatten
      val assets = assetExtractor.extractFrom(doc).map(l => linkToAbsoluteURL(docUrl.toURI, l)).flatten
      Page(docUrl, hash, links, assets)
    }
  } catch {
    case e: Throwable => Future(Left(e))
  }


  /**
    * linkToAbsoluteURL maps relative and absolute links on a document to an absolute URL resolving all common
    * usage of relative protocols, relative filenames, relative query strings and relative fragments.
    * It works for http, https and file protocols
    *
    * @param docUri base document URI
    * @param relUrl relative URL to be expanded
    * @return absolut URL as expanded from the relUrl argument relative to the docUri
    */
  def linkToAbsoluteURL(docUri: URI, relUrl: String): Option[URL] = {
    val URL_REGEX = "(?i)^(file:|(https?:)?//([a-z0-9\\.-]+))?(/[^\\?#]*?)?([^\\?#/]+)?(\\?.*?)?(#.*)?$".r
    val FILENAME_REGEX = "^(.+?)?([^/]+)?$".r
    val (docPath, docFile) = docUri.getPath match {
      case FILENAME_REGEX(path, filename) => (if (docUri.getScheme() == "file")
        path.substring(targetPath.length)
      else path, filename)
    }
    def nn(s: String, r: String = "", e: String = "") = if (s != null) s + r else e
    relUrl match {
      case URL_REGEX(base, http, host, path, file, query, fragment) =>
        val scheme = if (base == null) docUri.getScheme + ":"
        else if (base == "file:") base
        else if (base.startsWith("//")) docUri.getScheme + ":" else http
        val docRoot = if (base == null) (if (scheme == "file:") targetPath
        else "//" + docUri.getHost)
        else if (base != "file:") "//" + host else ""

        Some(new URL(scheme + docRoot +
          nn(path, nn(file) + nn(query) + nn(fragment), nn(docPath,
            nn(file, nn(query) + nn(fragment), nn(docFile,
              nn(query, nn(fragment), nn(docUri.getQuery,
                nn(fragment, "", nn(docUri.getFragment)), nn(fragment)))))))))

      case _: Any => None
    }
  }

}

class HttpScanner(target: URL, val followRedirects: Boolean) extends Scanner(target) {
  lazy val http = new dispatch.Http().configure(_.setFollowRedirect(followRedirects).
    setAllowPoolingConnections(true).setConnectTimeout(5000))

  def fetch(pageUrl: URL): Future[Either[Throwable, Array[Byte]]] = {
    val request = url(pageUrl.toString)
    //TODO augument the request by adding robot headers so that ROBOTS.txt is respected
    http(request OK as.Bytes).either
  }

  override def close(): Unit = {
    http.shutdown()
  }
}

class LocalScanner(target: URL) extends Scanner(target) {

  override def close(): Unit = {}

  override protected def fetch(pageUrl: URL): Future[Either[Throwable, Array[Byte]]] = {
    Future {
      try {
        val path = Paths.get(pageUrl.toURI)
        val f = if (!Files.isDirectory(path)) path else path.resolve("index.html")
        Right(Files.readAllBytes(f))
      } catch {
        case NonFatal(e) => Left(e)
      }
    }
  }
}