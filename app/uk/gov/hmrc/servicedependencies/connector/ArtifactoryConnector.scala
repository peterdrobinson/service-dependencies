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

package uk.gov.hmrc.servicedependencies.connector

import java.util.concurrent.Executors

import cats.implicits._
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.servicedependencies.config.ServiceDependenciesConfig
import uk.gov.hmrc.servicedependencies.model.{ScalaVersion, Version}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ArtifactoryConnector @Inject()(
  http  : HttpClient
, config: ServiceDependenciesConfig
){

  implicit val ec: ExecutionContext =
    ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(1))

  private lazy val authorization : Option[Authorization] =
    for {
      token <- config.artifactoryToken
    } yield Authorization(s"Bearer $token")

  def findLatestVersion(
    group       : String
  , artefact    : String
  , scalaVersion: ScalaVersion
  ): Future[Option[Version]] = {
    implicit val hc = HeaderCarrier(authorization = authorization)
    http.GET[Option[HttpResponse]](
        url         = s"${config.artifactoryBase}/api/search/latestVersion"
      , queryParams = Map( "g" -> group
                         , "a" -> s"$artefact${scalaVersion.asClassifier}"
                         ).toSeq
      )
      .map(_.map(_.body).flatMap(Version.parse))
  }

  def findLatestVersions(
    group       : String
  , artefact    : String
  ): Future[Map[ScalaVersion, Version]] =
    ScalaVersion.values
      .foldLeftM(Map.empty[ScalaVersion, Version]) {
        case (acc, scalaVersion) => findLatestVersion(group, artefact, scalaVersion)
                                       .map {
                                         case Some(v) => acc + (scalaVersion -> v)
                                         case None    => acc
                                       }
      }
}
