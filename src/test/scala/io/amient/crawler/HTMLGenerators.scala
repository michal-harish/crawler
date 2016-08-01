package io.amient.crawler

import org.htmlcleaner.{ContentNode, TagNode}
import org.scalacheck.Gen._
import org.scalacheck.{Arbitrary, Gen}

/**
  * Created by mharis on 27/07/2016.
  *
  * Random generators for documents, usage:
  *
  *   class ...Specification extends Properties(..) with HTMLGenerators {
  *     property("...") = forAll(docs) => { doc => ... }
  *   }
  *
  */
trait HTMLGenerators {

  val domains: Gen[String] = for {
    scheme <- oneOf("//", "http://", "https://")
    n <- choose(5, 15)
    domain2 <- listOfN(n, alphaNumChar)
    domain1 <- listOfN(3, alphaLowerChar)
  } yield scheme + domain2.mkString + "." + domain1.mkString

  val paths: Gen[String] = for {
    i <- choose(1, 3)
    path <- listOfN(i, listOfN(3, alphaLowerChar).map("/" + _.mkString))
  } yield path.mkString

  val queries: Gen[String] = for {
    hasQuery <- Gen.oneOf(false, true)
    query <- Gen.alphaStr
    hasFragment<- Gen.oneOf(false, true)
    fragment <- Gen.alphaStr
  } yield (if (hasQuery) "?" + query else "") + (if (hasFragment) "#" + fragment else "")

  val urls: Gen[String] = for {
    hasDomain <- Gen.oneOf(false, true)
    hasPath<- Gen.oneOf(false, true, true)
    domain <- domains
    path <- paths
    query <- queries
  } yield (if (hasDomain) domain + path else if (hasPath) path else "") + query


  case class Container(tag:String, elems: List[TagNode]) extends TagNode(tag) {
    elems.foreach(addChild)
  }


  abstract class Src (name:String, src: String) extends TagNode(name) {
    addAttribute("src", src)
  }

  abstract class Href(name:String, href: String) extends TagNode(name) {
    addAttribute("href", href)
  }

  case class Img(src: String) extends Src("img", src)

  case class Script(src: String) extends Src("script", src)

  case class Stylesheet(href: String) extends Href("link", href){
    addAttribute("rel", "stylesheet")
  }

  case class Anchor(href: String, text: String) extends Href("a", href) {
    addChild(new ContentNode(text))
  }

  case class Doc(head: Container, body: Container) extends TagNode("html") {
    addChild(head)
    addChild(body)
  }

  def imgs: Gen[Img] = for {
    url <- urls
    name <- Gen.alphaStr
  } yield Img(s"$url/$name.jpg")

  def anchors: Gen[Anchor] = for {
    href <- urls
    text <- Gen.alphaStr
  } yield Anchor(href, text)

  def scripts: Gen[Script] = for {
    src <- urls
  } yield Script(src)

  def stylesheet: Gen[Stylesheet] = for {
    href <- urls
  }  yield Stylesheet(href)

  def divs: Gen[Container] = for {
    numElems <- Gen.choose(1, 2)
    e <- listOfN(numElems, Gen.oneOf(imgs, divs, anchors))
  } yield Container("div", e)

  def docs: Gen[Doc] = for {
    scripts <- listOfN(3, scripts)
    numStyles <- Gen.choose(0, 2)
    styles <- listOfN(numStyles, stylesheet)
    body <- divs
  } yield Doc(Container("head", scripts ++ styles), Container("body", List(body)))

  implicit lazy val arbDoc: Arbitrary[Doc] = Arbitrary(docs)


}
