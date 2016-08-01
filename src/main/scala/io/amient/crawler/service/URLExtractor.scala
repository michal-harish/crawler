package io.amient.crawler.service

import org.htmlcleaner.TagNode

import scala.annotation.tailrec

/**
  * Hierarchy of extractors for extracting URLs from html documents.
  * TODO make this a functional hierarchy using currying where generic function implemented in trait would take
  * a function as argument. That way also assets can be extracted - using visitor generic function and then currying
  * Created by mharis on 27/07/2016.
  */
trait URLExtractor {

  /**
    *
    * @param document
    * @return a set of URLs represented as Strings that were extracted from the document
    */
  def extractFrom(document: TagNode): Set[String]

  /**
    * filterDoc - efficient version of recursive traversal with a given predicate
    *
    * @param doc
    * @param predicate
    * @return a list of TagNode objects that matched the predicate
    */
  def transformDoc[X](doc: TagNode)(predicate: TagNode => Option[X]): List[X] = {
    @tailrec
    def transformDoc(start: List[TagNode], acc: List[X]): List[X] = {
      if (start.isEmpty) {
        acc
      } else {
        transformDoc(start.map(_.getAllElements(false)).flatten, acc ++ start.map(predicate).flatten)
      }
    }
    transformDoc(List(doc), Nil)
  }
}


class SimpleAnchorExtractor extends URLExtractor {

  override def extractFrom(document: TagNode): Set[String] = transformDoc(document) {
    case tag if (tag.getName() == "a" && tag.hasAttribute("href")) => Some(tag.getAttributeByName("href"))
    case _ => None
  }.toSet

}

class StaticAssetExtractor extends URLExtractor {
  override def extractFrom(document: TagNode): Set[String] = transformDoc(document) {
    case tag if (tag.getName() == "img" && tag.hasAttribute("src")) => Some(tag.getAttributeByName("src"))
    case tag if (tag.getName() == "script" && tag.hasAttribute("src")) => Some(tag.getAttributeByName("src"))
    case tag if (tag.getName() == "stylesheet" && tag.hasAttribute("href")) => Some(tag.getAttributeByName("href"))
    case _ => None
  }.toSet
}

