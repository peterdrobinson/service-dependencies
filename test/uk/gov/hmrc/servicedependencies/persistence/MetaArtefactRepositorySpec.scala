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

package uk.gov.hmrc.servicedependencies.persistence

import java.time.Instant

import org.mockito.MockitoSugar
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.mongo.test.{PlayMongoRepositorySupport, CleanMongoCollectionSupport}
import uk.gov.hmrc.servicedependencies.model.{MetaArtefact, MetaArtefactModule, Version}

import scala.concurrent.ExecutionContext.Implicits.global

class MetaArtefactRepositorySpec
  extends AnyWordSpecLike
     with Matchers
     with MockitoSugar
     // with DefaultPlayMongoRepositorySupport[MetaArtefact] { // no index for findRepoNameByModule
     with PlayMongoRepositorySupport[MetaArtefact]
     with CleanMongoCollectionSupport
     with IntegrationPatience {

  override lazy val repository = new MetaArtefactRepository(mongoComponent)

  val metaArtefactModule =
    MetaArtefactModule(
      name                 = "sub-module",
      group                = "uk.gov.hmrc",
      sbtVersion           = Some(Version("1.4.9")),
      crossScalaVersions   = Some(List(Version("2.12.14"))),
      publishSkip          = Some(false),
      dependencyDotCompile = Some("ddc-graph"),
      dependencyDotTest    = Some("ddt-graph")
    )

  val metaArtefact =
    MetaArtefact(
      name               = "library",
      version            = Version("1.0.0"),
      uri                = "https://artefacts/metadata/library/library-v1.0.0.meta.tgz",
      gitUrl             = Some("https://github.com/hmrc/library.git"),
      dependencyDotBuild = Some("ddb-graph"),
      buildInfo          = Map(
                             "GIT_URL" -> "https://github.com/hmrc/library.git"
                           ),
      modules            = Seq(
                             metaArtefactModule,
                             metaArtefactModule.copy(name = "sub-module2")
                           ),
      created            = Instant.now()
    )

  val updatedMetaArtefact = metaArtefact.copy(modules = Seq(metaArtefactModule.copy(name = "sub-module3")))

  "add" should {
    "add correctly" in {
      (for {
         before <- repository.find(metaArtefact.name)
         _      =  before shouldBe None
         _      <- repository.add(metaArtefact)
         after  <- repository.find(metaArtefact.name)
         _      =  after shouldBe Some(metaArtefact)
       } yield ()
      ).futureValue
    }
    "upsert correctly" in {
      (for {
         before  <- repository.find(metaArtefact.name)
         _       =  before shouldBe None
         _       <- repository.add(metaArtefact)
         after   <- repository.find(metaArtefact.name)
         _       =  after shouldBe Some(metaArtefact)
         _       <- repository.add(updatedMetaArtefact)
         updated <- repository.find(metaArtefact.name)
         _       =  updated shouldBe Some(updatedMetaArtefact)
       } yield ()
     ).futureValue
    }
  }

  "find" should {
    "return the latest" in {
      (for {
         _      <- repository.add(metaArtefact.copy(version = Version("2.0.0")))
         _      <- repository.add(metaArtefact)
         found  <- repository.find(metaArtefact.name)
         _      =  found shouldBe Some(metaArtefact.copy(version = Version("2.0.0")))
       } yield ()
      ).futureValue
    }
  }

  "findRepoNameByModule" should {
    "find repo name" in {
      (for {
         _      <- repository.add(metaArtefact)
         name   <- repository.findRepoNameByModule(
                     group    = "uk.gov.hmrc",
                     artefact = "sub-module",
                     version  = Version("1.0.0")
                   )
         _      =  name shouldBe Some("library")
       } yield ()
      ).futureValue
    }

    "return data for any version if no match" in {
      (for {
         _      <- repository.add(metaArtefact)
         name   <- repository.findRepoNameByModule(
                     group    = "uk.gov.hmrc",
                     artefact = "sub-module",
                     version  = Version("0.0.1") // no match for this
                   )
         _      =  name shouldBe Some("library")
       } yield ()
      ).futureValue
    }
  }
}
