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

package uk.gov.hmrc.servicedependencies.connector

import javax.inject.Inject
import play.api.cache.AsyncCacheApi
import play.api.libs.json.Reads
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, StringContextOps}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.servicedependencies.connector.model.DeprecatedDependencies
import uk.gov.hmrc.servicedependencies.model.BobbyRules
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

class ServiceConfigsConnector @Inject()(
  httpClient    : HttpClient,
  servicesConfig: ServicesConfig,
  cache         : AsyncCacheApi
)(implicit ec: ExecutionContext
) {
  import HttpReads.Implicits._

  private implicit val hc: HeaderCarrier                    = HeaderCarrier()
  private implicit val reads: Reads[DeprecatedDependencies] = DeprecatedDependencies.reads

  private val serviceUrl: String = servicesConfig.baseUrl("service-configs")
  private val cacheExpiration: Duration =
    servicesConfig
      .getDuration("microservice.services.service-configs.cache.expiration")

  def getBobbyRules: Future[BobbyRules] =
    cache.getOrElseUpdate("bobby-rules", cacheExpiration) {
      httpClient
        .GET[DeprecatedDependencies](url"$serviceUrl/bobby/rules")
        .map { dependencies =>
           BobbyRules(
             (dependencies.libraries.toList ++ dependencies.plugins)
               .groupBy { dependency => (dependency.organisation, dependency.name) }
           )
        }
    }
}
