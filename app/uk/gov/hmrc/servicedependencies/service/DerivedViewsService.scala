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

import javax.inject.{Inject, Singleton}
import play.api.Logging
import uk.gov.hmrc.servicedependencies.persistence.derived.DerivedGroupArtefactRepository

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DerivedViewsService @Inject()(
  derivedGroupArtefactRepository: DerivedGroupArtefactRepository
)(implicit
  ec: ExecutionContext
) extends Logging{

  def generateAllViews() : Future[Unit] =
    derivedGroupArtefactRepository.populate()
      .recover {
        case e => logger.error("Failed to update derived collections", e)
      }
}
