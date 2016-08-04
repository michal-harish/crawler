package io.amient.crawler.service

import java.net.URL
import java.util.Properties
import java.util.concurrent.{Executors, LinkedBlockingQueue}

import dispatch.Future
import io.amient.crawler.domain._
import io.amient.crawler.task.Task

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.control.NonFatal

object Crawler {
  /**
    * Pause between each crawler step (crawler step may be requesting multiple pages at the same time)
    */
  val CONFIG_CRAWLER_PAUSE_MS = "crawler.pause.ms"

  /**
    * Maximum time how long a crawl of a single page is allowed to take in seconds
    */
  val CONFIG_CRAWLER_TIMEOUT_S = "crawler.timeout.s"

  /**
    * Maximum parallel scans that a single instance of crawler may do
    */
  val CONFIG_CRAWLER_MAX_PARALLELISM = "crawler.max.paralellism"

  /**
    * Scanner setting for automatically follow to redirected pages
    */
  val CONFIG_SCANNER_FOLLOW_REDIRECTS = "scanner.follow.redirects"

  /**
    * Number of seconds afte which the cached log files are invalidated
    */
  val CONFIG_TASK_CACHE_TTL_S = "task.cache.ttl.s"


}

/**
  * Crawler is the high-level service object that provides concurrent crawling capabilities with its main method:
  *
  * def crawl(baseUrls: Seq[String]): List[Either[Throwable, Page]]
  *
  * @param scanner Scanner implementation to be used
  */
class Crawler(scanner: Scanner, config: Properties) {

  import Crawler._

  val crawlPause = config.getProperty(CONFIG_CRAWLER_PAUSE_MS, "1000").toInt milliseconds
  val crawlTimeout = config.getProperty(CONFIG_CRAWLER_TIMEOUT_S, "10").toInt seconds
  val maxParallelScans = config.getProperty(CONFIG_CRAWLER_MAX_PARALLELISM, "3").toInt
  val targetBase = scanner.target.toString

  //this is the implicit executor for crawl Futures (see function crawl in method stream
  implicit val context = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(maxParallelScans))

  type E[X] = Either[Throwable, X]

  def close() = scanner.close()


  /**
    * toStream - this method is the fundamental unit work of the Crawler which is defined as recursive stream
    * evaluating so long as the consumer of the stream keeps requesting deeper levels and there is more links to
    * traverse.
    *
    * This allows the consumer to crawl incrementally and process results as they come as well as decide how
    * deep to continue in crawling the links while still preserving Uniqueness and Concurrency characteristics.
    *
    * The definition is lazy and streams are tail-recursive already. It starts with the baseUrl constructor
    * parameter of the class.
    *
    * @param task is the object that manages the state and logging of the stream's progress
    * @return a Stream of Either objects each of which may be a Throwable or a Page and is guaranteed to be unique
    */
  def stream(task: Task): Stream[E[Page]] = {

    val queue = new LinkedBlockingQueue[Future[E[Page]]]

    def linkFilter(link: URL): Boolean = {
      link.toString.startsWith(targetBase) && (!task.hasPageBeenRequested(link))
    }

    def crawl(urls: Iterable[URL]) = {
      var delayMs = 0L
      urls.foreach { url =>
        task.markPageAsRequested(url)
        queue.add(Future {
          val sleepMs = delayMs
          Thread.sleep(sleepMs)
          Await.result(scanner.scan(url), crawlTimeout)
        })
        delayMs += crawlPause.toMillis
      }
    }

    task.urlsToProcess match {
      case urls if urls.isEmpty => crawl(List(scanner.target))
      case urls => crawl(urls.filter(linkFilter))
    }

    def consecutive(): Stream[E[Page]] = {
      if (queue.isEmpty) return Stream.empty
      val queuedFuture = queue.take()
      try {
        val element = Await.result(queuedFuture, crawlTimeout) match {
          case Left(error) => Left(error)
          case Right(page) if (task.hasPageBeenVisited(page)) =>
            throw new IllegalStateException("Already crawled: " + page.url)
          case Right(page) => {
            task.markPageAsVisited(page)
            val links = page.links.filter(linkFilter)
            if (!links.isEmpty) {
              crawl(links)
            }
            Right(page)
          }
        }
        element #:: consecutive()

      } catch {
        case NonFatal(e) => Left(e) #:: consecutive()
      }
    }

    consecutive()

  }


}
