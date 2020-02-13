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

package uk.gov.hmrc.servicedependencies.controller.admin

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import uk.gov.hmrc.servicedependencies.persistence.LocksRepository
import uk.gov.hmrc.servicedependencies.service.DependencyDataUpdatingService

import scala.concurrent.ExecutionContext

@Singleton
class AdministrationController @Inject()(
    dependencyDataUpdatingService: DependencyDataUpdatingService
  , locksRepository              : LocksRepository
  , cc                           : ControllerComponents
  )(implicit ec: ExecutionContext
  ) extends BackendController(cc) {

  def reloadLibraryDependenciesForAllRepositories(force: Option[Boolean] = None) = Action { implicit request =>
    dependencyDataUpdatingService
      .reloadCurrentDependenciesDataForAllRepositories(force = force.getOrElse(false))
      .onFailure {
        case ex => throw new RuntimeException("reload of dependencies failed", ex)
      }
    Accepted("reload started")
  }

  def reloadLibraryVersions() = Action { implicit request =>
    dependencyDataUpdatingService
      .reloadLatestLibraryVersions
      .onFailure {
        case ex => throw new RuntimeException("reload of libraries failed", ex)
      }
    Accepted("reload started")
  }

  def reloadSbtPluginVersions() = Action { implicit request =>
    dependencyDataUpdatingService
      .reloadLatestSbtPluginVersions
      .onFailure {
        case ex => throw new RuntimeException("reload of sbt plugins failed", ex)
      }
    Accepted("reload started")
  }

  def dropCollection(collection: String) = Action.async { implicit request =>
    (collection match {
       case "locks"    => locksRepository.clearAllData
       case collection => dependencyDataUpdatingService.dropCollection(collection)
     }
    ).map(_ => Ok(s"$collection dropped"))
  }

  def clearUpdateDates = Action.async { implicit request =>
    dependencyDataUpdatingService.clearUpdateDates.map(rs => Ok(s"${rs.size} records updated"))
  }

  def mongoLocks() = Action.async { implicit request =>
    locksRepository.getAllEntries.map(locks => Ok(Json.toJson(locks)))
  }
}
