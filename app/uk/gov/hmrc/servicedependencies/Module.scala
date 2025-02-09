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

package uk.gov.hmrc.servicedependencies

import akka.stream.Materializer
import com.google.inject.AbstractModule
import play.api.libs.concurrent.MaterializerProvider

class Module() extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[scheduler.LatestVersionsReloadScheduler]).asEagerSingleton()
    bind(classOf[scheduler.SlugMetadataUpdateScheduler  ]).asEagerSingleton()
    bind(classOf[scheduler.BobbyRulesSummaryScheduler   ]).asEagerSingleton()
    bind(classOf[notification.SlugInfoUpdatedHandler    ]).asEagerSingleton()
    bind(classOf[notification.MetaArtefactUpdateHandler ]).asEagerSingleton()
    bind(classOf[notification.DeadLetterHandler         ]).asEagerSingleton()
    bind(classOf[Materializer                           ]).toProvider(classOf[MaterializerProvider])
  }
}
