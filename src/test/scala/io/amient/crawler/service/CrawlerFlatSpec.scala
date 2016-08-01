package io.amient.crawler.service

import java.util.Properties

import io.amient.crawler.domain.Page
import io.amient.crawler.task.Task
import org.scalatest.{FlatSpec, Matchers}

/**
  * Created by mharis on 29/07/2016.
  */
class CrawlerFlatSpec extends FlatSpec with Matchers {


  val scanner = new LocalScanner(getClass.getResource("/testsite/"))

  val config = new Properties() {
    put(Crawler.CONFIG_CRAWLER_PAUSE_MS, "0")
  }

  "crawler" should "work for a simple fixed scenario" in {

    val crawler = new Crawler(scanner, config)

    val stream = crawler.stream(new Task {})

    stream.foreach {
      case Right(page) => println(page.info)
      case Left(error) => println(error.getMessage)
    }
    stream.size should be(5)

    val ok: List[Page] = (for(Right(page) <- stream) yield page).toList
    val fail: List[Throwable] = (for(Left(page) <- stream) yield page).toList

    crawler.close()

    ok.size should be(4)
    val allAssets = ok.flatMap(_.assets)
    allAssets.size should be(8)
    allAssets.toSet.size should be(2)
    fail.size should be(1)
    fail.head.getMessage should equal(
      "Already crawled: file:/Users/mharis/git/iocrawler/build/resources/test/testsite/contactus.html")
  }

}
