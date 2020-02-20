/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.Instant

import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.{Mockito, MockitoSugar}
import org.scalatest.OptionValues
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.CurrentTimestampSupport
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.servicedependencies.config.ServiceDependenciesConfig
import uk.gov.hmrc.servicedependencies.config.model.{CuratedDependencyConfig, DependencyConfig}
import uk.gov.hmrc.servicedependencies.connector.{ArtifactoryConnector, GithubConnector, GithubSearchResults, ServiceConfigsConnector, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.servicedependencies.connector.model.RepositoryInfo
import uk.gov.hmrc.servicedependencies.controller.model.{Dependencies, Dependency}
import uk.gov.hmrc.servicedependencies.model._
import uk.gov.hmrc.servicedependencies.persistence._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class DependencyDataUpdatingServiceSpec
  extends AnyFunSpec
     with MockitoSugar
     with Matchers
     with GuiceOneAppPerTest
     with OptionValues
     with MongoSupport
     with IntegrationPatience {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure("metrics.jvm" -> false)
      .build()

  private val timeForTest = Instant.now()

  describe("reloadDependencyVersions") {
    it("should call the dependency version update function on the repository") {
      val boot = new Boot(CuratedDependencyConfig(
        sbtPlugins = Nil
      , libraries  = List(DependencyConfig(name = "libYY", group= "uk.gov.hmrc", latestVersion = None))
      , others     = Nil
      ))

      when(boot.mockArtifactoryConnector.findLatestVersion(group = "uk.gov.hmrc", artefact = "libYY"))
        .thenReturn(Future.successful(Map(ScalaVersion.SV_None -> Version("1.1.1"))))

      when(boot.mockDependencyVersionRepository.update(any()))
        .thenReturn(Future.successful(mock[MongoDependencyVersion]))

      boot.dependencyUpdatingService.reloadLatestDependencyVersions(HeaderCarrier()).futureValue

      verify(boot.mockDependencyVersionRepository, times(1))
        .update(MongoDependencyVersion(name = "libYY", group = "uk.gov.hmrc", version = Version("1.1.1"), updateDate = timeForTest))
      verifyZeroInteractions(boot.mockRepositoryLibraryDependenciesRepository)
    }
  }

  describe("reloadMongoRepositoryDependencyDataForAllRepositories") {

    def testReloadCurrentDependenciesDataForAllRepositories(
      repoLastUpdatedAt: Instant
    , shouldUpdate     : Boolean
    ) = {
      val boot = new Boot(CuratedDependencyConfig(
        sbtPlugins = List.empty
      , libraries  = List.empty
      , others     = List.empty
      ))

      val repositoryName = "repoXyz"

      val mongoRepositoryDependencies =
        MongoRepositoryDependencies(repositoryName, Nil, Nil, Nil, updateDate = timeForTest)

      val githubSearchResults =
        GithubSearchResults(
            sbtPlugins = Nil
          , libraries = Nil
          , others    = Nil
          )

      val repositoryInfo =
        RepositoryInfo(
          name          = repositoryName
        , createdAt     = Instant.EPOCH
        , lastUpdatedAt = repoLastUpdatedAt
        )

      when(boot.mockRepositoryLibraryDependenciesRepository.getAllEntries)
        .thenReturn(Future.successful(Seq(mongoRepositoryDependencies)))

      when(boot.mockTeamsAndRepositoriesConnector.getAllRepositories(any()))
        .thenReturn(Future.successful(Seq(repositoryInfo)))

      when(boot.mockGithubConnector.findVersionsForMultipleArtifacts(any()))
        .thenReturn(Right(githubSearchResults))

      when(boot.mockRepositoryLibraryDependenciesRepository.update(any()))
        .thenReturn(Future.successful(mongoRepositoryDependencies))

      val res = boot.dependencyUpdatingService
        .reloadCurrentDependenciesDataForAllRepositories(HeaderCarrier())
        .futureValue

      if (shouldUpdate) {
        res shouldBe Seq(mongoRepositoryDependencies)
        verify(boot.mockRepositoryLibraryDependenciesRepository, times(1))
          .update(eqTo(mongoRepositoryDependencies))
      } else {
        res shouldBe Nil
        verify(boot.mockRepositoryLibraryDependenciesRepository, Mockito.never())
          .update(any())
      }
    }

    it("should call the dependency update function to persist the dependencies if repo has been modified") {
      testReloadCurrentDependenciesDataForAllRepositories(
        repoLastUpdatedAt = timeForTest
      , shouldUpdate      = true
      )
    }

    it("should not call the dependency update function to persist the dependencies if repo has not been modified") {
      testReloadCurrentDependenciesDataForAllRepositories(
        repoLastUpdatedAt = Instant.EPOCH
      , shouldUpdate      = false
      )
    }
  }

  class Boot(dependencyConfig: CuratedDependencyConfig) {
    val mockServiceDependenciesConfig               = mock[ServiceDependenciesConfig]
    val mockRepositoryLibraryDependenciesRepository = mock[RepositoryLibraryDependenciesRepository]
    val mockDependencyVersionRepository             = mock[DependencyVersionRepository]
    val mockTeamsAndRepositoriesConnector           = mock[TeamsAndRepositoriesConnector]
    val mockArtifactoryConnector                    = mock[ArtifactoryConnector]
    val mockGithubConnector                         = mock[GithubConnector]
    val mockServiceConfigsConnector                 = mock[ServiceConfigsConnector]

    when(mockServiceDependenciesConfig.curatedDependencyConfig)
      .thenReturn(dependencyConfig)

    when(mockServiceConfigsConnector.getBobbyRules)
      .thenReturn(Future.successful(BobbyRules(Map.empty)))

    val dependencyUpdatingService = new DependencyDataUpdatingService(
        mockServiceDependenciesConfig
      , mockRepositoryLibraryDependenciesRepository
      , mockDependencyVersionRepository
      , mockTeamsAndRepositoriesConnector
      , mockArtifactoryConnector
      , mockGithubConnector
      , mockServiceConfigsConnector
      ) {
        override def now: Instant = timeForTest
      }
  }
}
