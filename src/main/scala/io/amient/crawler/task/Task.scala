package io.amient.crawler.task

import java.io._
import java.net.URL
import java.nio.channels.{Channels, FileChannel}
import java.nio.file.StandardOpenOption._
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.Callable

import scala.concurrent.duration._
import io.amient.crawler.domain.Page
import io.amient.crawler.service.{Crawler, HttpScanner}

import scala.collection.Searching.search
import scala.collection.mutable
import scala.util.control.NonFatal

/**
  * Represents
  * Created by mharis on 29/07/2016.
  */
trait Task {
  /**
    * State vriables - they don't need to be concurrent structures as they are always accessed from the same
    * caller Thread which handles the crawler Streams
    */
  protected val urlsRequested = mutable.Set[URL]()
  protected val pagesVisited = mutable.Set[Page]()

  def urlsToProcess: Set[URL] = urlsRequested.toSet

  def hasPageBeenRequested(link: URL) = urlsRequested.contains(link)

  def hasPageBeenVisited(page: Page) = pagesVisited.contains(page)

  def markPageAsRequested(url: URL) = urlsRequested += url

  def markPageAsVisited(page: Page) = pagesVisited += page

}

case class LoggedTask(target: URL, config: Properties)(processor: (Either[Throwable, Page]) => Unit)
  extends Callable[Option[Throwable]] with Task{

  val id = MessageDigest.getInstance("MD5").digest(target.toString.getBytes).map("%02X".format(_)).mkString

  val scanner = new HttpScanner(target, config.getProperty(Crawler.CONFIG_SCANNER_FOLLOW_REDIRECTS, "true").toBoolean)

  val crawler = new Crawler(scanner, config)

  val datadir = Paths.get("./log").toAbsolutePath

  val logfile = datadir.resolve(Paths.get(id + ".crawl"))

  val cacheTtl = config.getProperty(Crawler.CONFIG_TASK_CACHE_TTL_S, "86400").toInt seconds

  {
    if (!Files.exists(datadir)) {
      println(s"Creating data directory for logs: " + datadir)
      Files.createDirectory(datadir)
    }
    if (!Files.exists(logfile)) {
      println(s"Starting a new task $id for: $target")
      Files.createFile(logfile)
    } else {
      val created = Files.readAttributes(logfile, classOf[BasicFileAttributes]).creationTime()
      val ageInSeconds = (System.currentTimeMillis() - created.toMillis)/ 1000
      if (ageInSeconds > cacheTtl.toSeconds) {
        println(s"Found previous task for: $target but is too old")
        Files.delete(logfile)
        println(s"Starting a new task $id for: $target")
        Files.createFile(logfile)
      } else {
        println(s"Continuing task $id for: $target")
      }

    }
  }


  override def call: Option[Throwable] = try {
    val fileChannel = FileChannel.open(logfile, READ, CREATE, WRITE)
    val lock = fileChannel.tryLock()
    try {
      val oos = bootstrap(fileChannel)
      val stream = crawler.stream(this)
      for (element <- stream) {
        element match {
          case Right(page) => oos.writeObject(page)
          case Left(error) => oos.writeObject(error)
        }
        oos.flush()
        processor(element)
      }
      println(s"Completed task $id for: $target")
      None
    } finally {
      lock.release()
    }
  } catch {
    case NonFatal(e) =>
      println(s"Failed task $id for: $target with: " + e)
      Some(e)
  } finally {
    crawler.close
  }

  private def bootstrap(channel: FileChannel): ObjectOutputStream = {
    def maybeWriteHeader(): ObjectOutputStream = {
      val oos = new ObjectOutputStream(Channels.newOutputStream(channel))
      if (pagesVisited.isEmpty) oos.writeObject(target)
      oos
    }
    try {
      val iis = new ObjectInputStream(Channels.newInputStream(channel))
      while (true) {
        iis.readObject() match {
          case url: URL => assert(url == target)
          case error: Throwable => processor(Left(error))
          case page: Page =>
            markPageAsVisited(page)
            page.links.foreach(markPageAsRequested)
            processor(Right(page))
        }
      }
      maybeWriteHeader()
    } catch {
      case _: EOFException | _: StreamCorruptedException | _: InvalidClassException =>
        if (pagesVisited.size > 0) println(s"Num. URLs restored as visited: ${pagesVisited.size}")
        pagesVisited.foreach(page => page.url)
        maybeWriteHeader()
    }
  }


}
