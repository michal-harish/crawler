package io.amient.crawler.domain

import java.net.URL

/**
  * Main domain object representing a crawled Page. Note that the actual html is not stored but instead
  * the equality is build around url and extracted links and assets but the html content is represented by the hash
  * which is also used as the hashCode.
  *
  * Created by mharis on 27/07/2016.
  */
case class Page(url: URL, contentHash: Int, val links: Set[URL], val assets: Set[URL]) extends Serializable {

  def info: String =  s"$url | assets: ${assets.size} | links: ${links.size}"

  def report: String = s"$url" + assets.map(u => s"\n\t- $u").mkString

  override def toString = s"$url" + assets.map(u => s"\n\t- $u").mkString + links.map(u => s"\n\t[ $u ]").mkString

  /**
    * hashCode of the content is calculated by content processor
    *
    * @return
    */
  override def hashCode = contentHash

  /**
    * equals doesn't use url value because url represents a handle by which the page is accessed, not its contents
    *
    * @param that
    * @return
    */
  override def equals(that: Any) = that match {
    case other: Page => (links == other.links) && (assets == other.assets)
    case _ => false
  }
}


