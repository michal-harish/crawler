package io.amient.crawler

import java.net.URL
import java.util.Properties
import java.util.concurrent.Executors

import io.amient.crawler.service.Crawler
import io.amient.crawler.task.LoggedTask

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * Created by mharis on 27/07/2016.
  */
object CrawlConsoleApplication extends App {

  if (args.length == 0) {
    println("Missing arguments, usage:\n./build/scripts/crawler <url-to-crawl> [<target-url-2> [...]]\n\n")
    System.exit(1)
  }

  val config = new Properties {
    put(Crawler.CONFIG_CRAWLER_PAUSE_MS, "1000") //i.e. at most 1 request per second per task
    put(Crawler.CONFIG_CRAWLER_TIMEOUT_S, "30") //max seconds for any single page to complete
    put(Crawler.CONFIG_CRAWLER_MAX_PARALLELISM, "5") // crawl max 5 pages at the same time
    put(Crawler.CONFIG_SCANNER_FOLLOW_REDIRECTS, "true")
    put(Crawler.CONFIG_TASK_CACHE_TTL_S, "600") //expire tasks after 10 minutes for testing
  }

  val executor = Executors.newCachedThreadPool()

  var failed = 0
  var succeeded = 0
  //create task for each argument and process in-stream
  val errors = executor.invokeAll(args.toList.map(arg => LoggedTask(new URL(arg), config) {
    case Right(page) => succeeded += 1; println( page.report)
    case Left(error) => failed += 1; error.printStackTrace(System.out)
  }).asJava).asScala.map(javaFuture => Future {
    javaFuture.get
  })

  try {
    Await.result(Future.sequence(errors), 10 hours).foreach {
      case None => println("Task completed successfully")
      case Some(error: Throwable) => println("Task failed with: "); error.printStackTrace(System.out)
    }

    println("Total pages crawled successfully: " + succeeded)
    println("Total pages failed: " + failed)

  } finally {
    executor.shutdownNow()
    System.exit(0) //bug in dispatch shutdown - some threads are hanging
  }


}


