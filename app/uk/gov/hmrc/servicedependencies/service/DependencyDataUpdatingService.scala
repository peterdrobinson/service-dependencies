/*
 * Copyright 2017 HM Revenue & Customs
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

import org.slf4j.LoggerFactory
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.modules.reactivemongo.MongoDbConnection
import uk.gov.hmrc.servicedependencies.config.ServiceDependenciesConfig
import uk.gov.hmrc.servicedependencies.{LibraryDependencyState, RepositoryDependencies, SbtPluginDependencyState}
import uk.gov.hmrc.servicedependencies.model.{LibraryVersion, MongoLibraryVersion, MongoRepositoryDependencies, MongoSbtPluginVersion}
import uk.gov.hmrc.servicedependencies.presistence._

import scala.concurrent.Future

trait DependencyDataUpdatingService {

  def repositoryDependencyMongoLock: MongoLock
  def libraryMongoLock: MongoLock
  def sbtPluginMongoLock: MongoLock

  def reloadSbtPluginVersions(timeStampGenerator:() => Long): Future[Seq[MongoSbtPluginVersion]]
  def reloadLibraryVersions(timeStampGenerator:() => Long): Future[Seq[MongoLibraryVersion]]
  def reloadDependenciesDataForAllRepositories(timeStampGenerator:() => Long): Future[Seq[MongoRepositoryDependencies]]

  def getAllCuratedLibraries(): Future[Seq[MongoLibraryVersion]]
  def getAllRepositoriesDependencies(): Future[Seq[MongoRepositoryDependencies]]

  def getDependencyVersionsForRepository(repositoryName: String): Future[Option[RepositoryDependencies]]

  lazy val releasesConnector = new DeploymentsDataSource(config)
  lazy val teamsAndRepositoriesClient = new TeamsAndRepositoriesClient(config.teamsAndRepositoriesServiceUrl)

  lazy val dependenciesDataSource = new DependenciesDataSource(releasesConnector, teamsAndRepositoriesClient, config)

  val config: ServiceDependenciesConfig

}


class DefaultDependencyDataUpdatingService(override val config: ServiceDependenciesConfig)
  extends DependencyDataUpdatingService with MongoDbConnection {

  lazy val logger = LoggerFactory.getLogger(this.getClass)

  lazy val repositoryLibraryDependenciesRepository: RepositoryLibraryDependenciesRepository = new MongoRepositoryLibraryDependenciesRepository(db)
  lazy val libraryVersionRepository: LibraryVersionRepository = new MongoLibraryVersionRepository(db)
  lazy val sbtPluginVersionRepository: SbtPluginVersionRepository = new MongoSbtPluginVersionRepository(db)

  override def repositoryDependencyMongoLock: MongoLock = new MongoLock(db, "repository-dependencies-data-reload-job")

  override def libraryMongoLock: MongoLock = new MongoLock(db, "libraries-data-reload-job")
  override def sbtPluginMongoLock: MongoLock = new MongoLock(db, "sbt-plugin-data-reload-job")

  lazy val curatedDependencyConfig = config.curatedDependencyConfig

  override def reloadSbtPluginVersions(timeStampGenerator:() => Long): Future[Seq[MongoSbtPluginVersion]] = {
    runMongoUpdate(sbtPluginMongoLock) {
      val sbtPluginVersions = dependenciesDataSource.getLatestSbtPluginVersions(curatedDependencyConfig.sbtPlugins)

      Future.sequence(sbtPluginVersions.map { x =>
        sbtPluginVersionRepository.update(MongoSbtPluginVersion(x.sbtPluginName, x.version, timeStampGenerator()))
      })
    }
  }


  override def reloadLibraryVersions(timeStampGenerator:() => Long): Future[Seq[MongoLibraryVersion]] = {
    runMongoUpdate(libraryMongoLock) {
      val latestLibraryVersions = dependenciesDataSource.getLatestLibrariesVersions(curatedDependencyConfig.libraries)

      Future.sequence(latestLibraryVersions.map { x =>
        libraryVersionRepository.update(MongoLibraryVersion(x.libraryName, x.version, timeStampGenerator()))
      })
    }
  }

  override def reloadDependenciesDataForAllRepositories(timeStampGenerator:() => Long): Future[Seq[MongoRepositoryDependencies]] = {
    runMongoUpdate(repositoryDependencyMongoLock) {
      for {
        currentDependencyEntries <- repositoryLibraryDependenciesRepository.getAllEntries
        libraryDependencies <- dependenciesDataSource.persistDependenciesForAllRepositories(curatedDependencyConfig, timeStampGenerator, currentDependencyEntries, repositoryLibraryDependenciesRepository.update)
      } yield libraryDependencies

    }
}
  private def runMongoUpdate[T](mongoLock: MongoLock)(f: => Future[T]) =
    mongoLock.tryLock {
      logger.info(s"Starting mongo update")
      f
    } map {
      _.getOrElse(throw new RuntimeException(s"Mongo is locked for ${mongoLock.lockId}"))
    } map { r =>
      logger.info("mongo update completed")
      r
    }

  //!@ test the sbt plugin stuff
  override def getDependencyVersionsForRepository(repositoryName: String): Future[Option[RepositoryDependencies]] =
    for {
      dependencies <- repositoryLibraryDependenciesRepository.getForRepository(repositoryName)
      libraryReferences <- libraryVersionRepository.getAllEntries
      sbtPluginReferences <- sbtPluginVersionRepository.getAllEntries
    } yield
      dependencies.map { dep =>
        RepositoryDependencies(
          repositoryName,
          dep.libraryDependencies.map(d => LibraryDependencyState(d.libraryName, d.currentVersion, libraryReferences.find(mlv => mlv.libraryName == d.libraryName).flatMap(_.version))),
          dep.sbtPluginDependencies.map(d => SbtPluginDependencyState(d.sbtPluginName, d.currentVersion, sbtPluginReferences.find(mlv => mlv.sbtPluginName == d.sbtPluginName).flatMap(_.version)))
        )
      }

  override def getAllCuratedLibraries(): Future[Seq[MongoLibraryVersion]] =
    libraryVersionRepository.getAllEntries

  override def getAllRepositoriesDependencies(): Future[Seq[MongoRepositoryDependencies]] =
    repositoryLibraryDependenciesRepository.getAllEntries
}