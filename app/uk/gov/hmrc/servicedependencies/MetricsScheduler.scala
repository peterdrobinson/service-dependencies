/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.servicedependencies

import akka.actor.ActorSystem
import com.kenshoo.play.metrics.Metrics
import javax.inject.Inject
import org.joda.time.Duration
import play.api.{Configuration, Logger}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DefaultDB
import uk.gov.hmrc.lock.{ExclusiveTimePeriodLock, LockRepository}
import uk.gov.hmrc.metrix.MetricOrchestrator
import uk.gov.hmrc.metrix.persistence.MongoMetricRepository
import uk.gov.hmrc.servicedependencies.service.RepositoryDependenciesSource
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.control.NonFatal

class MetricsScheduler @Inject()(
  actorSystem: ActorSystem,
  configuration: Configuration,
  metrics: Metrics,
  reactiveMongoComponent: ReactiveMongoComponent,
  repositoryDependenciesSource: RepositoryDependenciesSource) {

  private val refreshIntervalKey = "repositoryDependencies.metricsGauges.interval"

  lazy val refreshIntervalMillis: Long =
    configuration
      .getMilliseconds(refreshIntervalKey)
      .getOrElse(throw new RuntimeException(s"$refreshIntervalKey not specified"))

  implicit lazy val mongo: () => DefaultDB = reactiveMongoComponent.mongoConnector.db

  val lock = new ExclusiveTimePeriodLock {
    override def repo: LockRepository  = new LockRepository()
    override def lockId: String        = "repositoryDependenciesLock"
    override def holdLockFor: Duration = new org.joda.time.Duration(refreshIntervalMillis)
  }

  val metricOrchestrator = new MetricOrchestrator(
    metricSources    = List(repositoryDependenciesSource),
    lock             = lock,
    metricRepository = new MongoMetricRepository(),
    metricRegistry   = metrics.defaultRegistry
  )

  actorSystem.scheduler.schedule(1.minute, refreshIntervalMillis.milliseconds) {
    metricOrchestrator
      .attemptToUpdateRefreshAndResetMetrics( _ => true)
      .map(_.andLogTheResult())
      .recover {
        case NonFatal(e) => Logger.error(s"An error occurred processing metrics: ${e.getMessage}", e)
      }
  }

}