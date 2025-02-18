/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.servicedependencies.service

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DependencyGraphParserSpec
  extends AnyWordSpec
     with Matchers {
  import DependencyGraphParser._

  val dependencyGraphParser = new DependencyGraphParser

  "DependencyGraphParser.parse" should {
    "return dependencies with evictions applied" in {
      val source = scala.io.Source.fromResource("slugs/dependencies-compile.dot")
      val graph = dependencyGraphParser.parse(source.mkString)
      graph.dependencies shouldBe List(
        Node("com.typesafe.play:filters-helpers_2.12:2.7.5"),
        Node("org.typelevel:cats-core_2.12:2.2.0"),
        Node("org.typelevel:cats-kernel_2.12:2.2.0"),
        Node("uk.gov.hmrc:file-upload_2.12:2.22.0")
      )
    }

    "return dependencies from maven generated files" in {
      val source = scala.io.Source.fromResource("slugs/dependencies-maven.dot")
      val graph = dependencyGraphParser.parse(source.mkString)
      graph.dependencies should contain allOf(
        Node("com.google.guava:guava:jar:18.0:compile"),
        Node("com.zaxxer:HikariCP:jar:2.5.1:compile"),
        Node("javax.xml.stream:stax-api:jar:1.0-2:compile"),
        Node("org.apache.commons:commons-lang3:jar:3.7:compile"),
        Node("org.springframework.ws:spring-ws-core:jar:2.1.4.RELEASE:compile"),
        Node("org.springframework.ws:spring-ws-support:jar:2.1.4.RELEASE:compile"),
        Node("org.springframework.ws:spring-xml:jar:2.1.4.RELEASE:compile"),
        Node("org.springframework:spring-oxm:jar:3.2.15.RELEASE:compile"),
        Node("uk.gov.hmrc.jdc:emcs:war:3.226.0"),
        Node("uk.gov.hmrc.rehoming:event-auditing:jar:2.0.0:compile"),
        Node("uk.gov.hmrc.rehoming:rehoming-common:jar:7.41.0:compile"),
        Node("wsdl4j:wsdl4j:jar:1.6.1:compile"),
        Node("xerces:xercesImpl:jar:2.12.0:compile")
      )
    }
  }

  "DependencyGraphParser.pathToRoot" should {
    "return path to root" in {
      val source = scala.io.Source.fromResource("slugs/dependencies-compile.dot")
      val graph = dependencyGraphParser.parse(source.mkString)
      graph.pathToRoot(Node("org.typelevel:cats-kernel_2.12:2.2.0")) shouldBe List(
        Node("org.typelevel:cats-kernel_2.12:2.2.0"),
        Node("org.typelevel:cats-core_2.12:2.2.0"),
        Node("uk.gov.hmrc:file-upload_2.12:2.22.0")
      )
    }

    "work with maven dependencies" in {
      val source = scala.io.Source.fromResource("slugs/dependencies-maven.dot")
      val graph = dependencyGraphParser.parse(source.mkString)
      graph.pathToRoot(Node("javax.xml.stream:stax-api:jar:1.0-2:compile")) shouldBe List(
        Node("javax.xml.stream:stax-api:jar:1.0-2:compile"),
        Node("org.springframework.ws:spring-xml:jar:2.1.4.RELEASE:compile"),
        Node("org.springframework.ws:spring-ws-support:jar:2.1.4.RELEASE:compile"),
        Node("uk.gov.hmrc.jdc:emcs:war:3.226.0")
      )
    }

    "work with emcs dependencies" in {
      val source = scala.io.Source.fromResource("slugs/dependencies-emcs.dot")
      val graph = dependencyGraphParser.parse(source.mkString)
      graph.dependencies.map(d => (d.group, d.artefact, d.version, d.scalaVersion)) should not be empty
    }

    "work with trailing spaces" in {
      // note the trailing spaces and tabs
      val input = "digraph \"uk.gov.hmrc.jdc:platops-example-classic-service:war:0.53.0\" { \n" +
        "\t\"uk.gov.hmrc.jdc:platops-example-classic-service:war:0.53.0\" -> \"uk.gov.hmrc.jdc:platops-example-classic-service-business:jar:0.53.0:compile\" ; \n" +
        " } "
      val graph = dependencyGraphParser.parse(input)
      graph.dependencies should contain allOf(
        Node("uk.gov.hmrc.jdc:platops-example-classic-service:war:0.53.0"),
        Node("uk.gov.hmrc.jdc:platops-example-classic-service-business:jar:0.53.0:compile"),
      )
    }
  }

  "Node" should {
    "parse name without scalaVersion" in {
      val n = Node("default:project:0.1.0-SNAPSHOT")
      n.group shouldBe "default"
      n.artefact shouldBe "project"
      n.version shouldBe "0.1.0-SNAPSHOT"
      n.scalaVersion shouldBe None
    }

    "parse name with scalaVersion" in {
      val n = Node("org.scala-lang.modules:scala-xml_2.12:1.3.0")
      n.group shouldBe "org.scala-lang.modules"
      n.artefact shouldBe "scala-xml"
      n.version shouldBe "1.3.0"
      n.scalaVersion shouldBe Some("2.12")
    }

    "parse name with scalaVersion and underscores" in {
      val n = Node("org.test.modules:test_artefact_2.12:1.0.0")
      n.group shouldBe "org.test.modules"
      n.artefact shouldBe "test_artefact"
      n.version shouldBe "1.0.0"
      n.scalaVersion shouldBe Some("2.12")
    }

    "parse name with type" in {
      val n = Node("uk.gov.hmrc.jdc:emcs:war:3.226.0")
      n.group shouldBe "uk.gov.hmrc.jdc"
      n.artefact shouldBe "emcs"
      n.version shouldBe "3.226.0"
      n.scalaVersion shouldBe None
    }

    "parse name with scope" in {
      val n = Node("javax.xml.stream:stax-api:1.0-2:compile")
      n.group shouldBe "javax.xml.stream"
      n.artefact shouldBe "stax-api"
      n.version shouldBe "1.0-2"
      n.scalaVersion shouldBe None
    }

    "parse name with type and scope" in {
      val n = Node("javax.xml.stream:stax-api:jar:1.0-2:compile")
      n.group shouldBe "javax.xml.stream"
      n.artefact shouldBe "stax-api"
      n.version shouldBe "1.0-2"
      n.scalaVersion shouldBe None
    }

    "parse name with type and classifier and scope" in {
      val n = Node("io.netty:netty-transport-native-epoll:jar:linux-x86_64:4.0.51.Final:compile")
      n.group shouldBe "io.netty"
      n.artefact shouldBe "netty-transport-native-epoll"
      n.version shouldBe "4.0.51.Final"
      n.scalaVersion shouldBe None
    }

    "parse name with type and classifier" in {
      val n = Node("io.netty:netty-transport-native-epoll:jar:linux-x86_64:4.0.51.Final")
      n.group shouldBe "io.netty"
      n.artefact shouldBe "netty-transport-native-epoll"
      n.version shouldBe "4.0.51.Final"
      n.scalaVersion shouldBe None
    }

    "parse a version that does not start with a number" in {
      val n = Node("ir.middleware:middleware-utils:jar:J22_2.9:compile")
      n.group shouldBe "ir.middleware"
      n.artefact shouldBe "middleware-utils"
      n.version shouldBe "J22_2.9"
      n.scalaVersion shouldBe None
    }

    "parse alternative type" in {
      val n = Node("uk.gov.hmrc.jdc.rsa:framework-core:test-jar:tests:7.69.0:test")
      n.group shouldBe "uk.gov.hmrc.jdc.rsa"
      n.artefact shouldBe "framework-core"
      n.version shouldBe "7.69.0"
      n.scalaVersion shouldBe None
    }
  }
}
