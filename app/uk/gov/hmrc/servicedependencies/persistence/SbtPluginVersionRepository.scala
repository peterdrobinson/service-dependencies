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

package uk.gov.hmrc.servicedependencies.persistence

import com.google.inject.{Inject, Singleton}
import com.mongodb.BasicDBObject
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Indexes.hashed
import org.mongodb.scala.model.{IndexModel, IndexOptions, ReplaceOptions}
import play.api.Logger
import play.api.libs.json.Json
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoCollection
import uk.gov.hmrc.mongo.throttle.{ThrottleConfig, WithThrottling}
import uk.gov.hmrc.servicedependencies.model._
import uk.gov.hmrc.servicedependencies.util.FutureHelpers

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SbtPluginVersionRepository @Inject()(
    mongoComponent    : MongoComponent
  , futureHelpers     : FutureHelpers
  , val throttleConfig: ThrottleConfig
  )(implicit ec: ExecutionContext
  ) extends PlayMongoCollection[MongoSbtPluginVersion](
    collectionName = "sbtPluginVersions"
  , mongoComponent = mongoComponent
  , domainFormat   = MongoSbtPluginVersion.format
  , indexes        = Seq(
                       IndexModel(hashed("sbtPluginName"), IndexOptions().name("sbtPluginNameIdx").background(true))
                     )
  , optSchema      = Some(BsonDocument(MongoSbtPluginVersion.schema))
  ) with WithThrottling {

  val logger: Logger = Logger(this.getClass)

  def update(sbtPluginVersion: MongoSbtPluginVersion): Future[MongoSbtPluginVersion] = {
    logger.debug(s"writing $sbtPluginVersion")
    futureHelpers
      .withTimerAndCounter("mongo.update") {
        collection
          .replaceOne(
              filter      = and( equal("sbtPluginName", sbtPluginVersion.name)
                               , equal("group"        , sbtPluginVersion.group)
                               )
            , replacement = sbtPluginVersion
            , options     = ReplaceOptions().upsert(true)
            )
          .toThrottledFuture
          .map(_ => sbtPluginVersion)
      }
      .recover {
        case e =>
          throw new RuntimeException(s"failed to persist SbtPluginVersion $sbtPluginVersion: ${e.getMessage}", e)
      }
  }

  def getAllEntries: Future[Seq[MongoSbtPluginVersion]] =
    collection.find()
      .toThrottledFuture

  def clearAllData: Future[Boolean] =
    collection.deleteMany(new BasicDBObject())
      .toThrottledFuture
      .map(_.wasAcknowledged())
}
